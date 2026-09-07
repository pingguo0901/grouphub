// Telegram Bot 包车订单 Webhook 中间层
// 红线：所有订单强制 account_type=private，绝不参与报税
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  try {
    const update = await req.json();
    const msg = update.message || update.callback_query?.message;
    if (!msg || !msg.text) {
      return new Response("{}", { headers: { "Content-Type": "application/json" } });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );
    const botToken = Deno.env.get("TELEGRAM_BOT_TOKEN");
    const tgUserId = String(msg.from.id);
    const chatId = msg.chat.id;
    const text = String(msg.text).trim();
    const senderName = msg.from.first_name || msg.from.username || tgUserId;

    let reply = "收到";

    // 记录用户消息
    await supabase.from("tg_bot_message_log").insert({
      tg_user_id: tgUserId, message_in: text, message_out: "", timestamp: new Date().toISOString(),
    });

    // 确保用户已注册
    await upsertUser(supabase, tgUserId, senderName);

    if (text.startsWith("/start") || text.startsWith("/help")) {
      reply = "🚗 星域臻旅包车 Bot\n\n" +
        "📝 /neworder 行程|金额|备注 —— 提交新包车订单\n" +
        "📋 /myorders —— 查询我的订单\n" +
        "✅ /pay 订单号|金额|方式 —— 登记收款\n" +
        "💸 /settle 订单号|金额 —— 司机结算确认\n" +
        "🔔 /status 订单号|状态 —— 改订单状态(new/matched/ongoing/completed/cancelled)\n\n" +
        "格式示例：\n/neworder 吉隆坡到新山|500|明天早上8点";
    } else if (text.startsWith("/neworder")) {
      const parts = text.replace("/neworder", "").trim().split("|");
      const trip = (parts[0] || "").trim();
      const amount = parseFloat(parts[1] || "0");
      const note = (parts[2] || "").trim();
      if (!trip || amount <= 0) {
        reply = "格式：/neworder 行程|金额|备注\n例：/neworder 吉隆坡到新山|500|明天8点";
      } else {
        const orderNo = genOrderNo();
        const { error } = await supabase.from("tg_charter_order").insert({
          order_no: orderNo, tg_customer_id: tgUserId, trip_details: trip,
          total_order_amount: amount, order_status: "new", note,
          account_type: "private", business_industry: "ElectronicTrade",
        });
        reply = error
          ? "下单失败：" + error.message
          : "✅ 订单已提交\n订单号：" + orderNo + "\n行程：" + trip + "\n金额：RM " + amount;
      }
    } else if (text.startsWith("/myorders")) {
      const { data } = await supabase.from("tg_charter_order")
        .select("order_no, trip_details, order_status, total_order_amount")
        .eq("tg_customer_id", tgUserId).order("created_at", { ascending: false }).limit(10);
      if (!data || data.length === 0) reply = "暂无订单";
      else reply = "📋 我的订单：\n" + data.map((o) =>
        `• ${o.order_no} [${o.order_status}] ${o.trip_details} RM${o.total_order_amount}`
      ).join("\n");
    } else if (text.startsWith("/pay")) {
      const parts = text.replace("/pay", "").trim().split("|");
      const orderNo = (parts[0] || "").trim();
      const amount = parseFloat(parts[1] || "0");
      const method = (parts[2] || "DuitNow").trim();
      await supabase.from("tg_order_payment").insert({
        order_no: orderNo, payment_type: "customer_pay", amount,
        payment_method: method, payment_status: "confirmed",
        operator_tg_user_id: tgUserId, account_type: "private",
      });
      reply = `✅ 已登记收款：${orderNo} RM${amount}（${method}）`;
    } else if (text.startsWith("/settle")) {
      const parts = text.replace("/settle", "").trim().split("|");
      const orderNo = (parts[0] || "").trim();
      const amount = parseFloat(parts[1] || "0");
      await supabase.from("tg_order_payment").insert({
        order_no: orderNo, payment_type: "driver_settle", amount,
        payment_method: "转账", payment_status: "confirmed",
        operator_tg_user_id: tgUserId, account_type: "private",
      });
      await supabase.from("tg_charter_order").update({ order_status: "completed", updated_at: new Date().toISOString() }).eq("order_no", orderNo);
      reply = `💸 已登记司机结算：${orderNo} RM${amount}`;
      await notifyOrderParties(supabase, botToken, orderNo, "completed");
    } else if (text.startsWith("/assign")) {
      const parts = text.replace("/assign", "").trim().split("|");
      const orderNo = (parts[0] || "").trim();
      const driverId = (parts[1] || "").trim();
      const { error } = await supabase.from("tg_charter_order").update({
        tg_driver_id: driverId, order_status: "matched", updated_at: new Date().toISOString()
      }).eq("order_no", orderNo);
      reply = error ? "指派失败：" + error.message : `✅ 订单 ${orderNo} 已指派司机 ${driverId}`;
      if (!error) await notifyOrderParties(supabase, botToken, orderNo, "matched");
    } else if (text.startsWith("/status")) {
      const parts = text.replace("/status", "").trim().split("|");
      const orderNo = (parts[0] || "").trim();
      const st = (parts[1] || "ongoing").trim();
      const { error } = await supabase.from("tg_charter_order").update({ order_status: st, updated_at: new Date().toISOString() }).eq("order_no", orderNo);
      reply = error ? "改状态失败：" + error.message : `✅ 订单 ${orderNo} 状态已改为 ${st}`;
      if (!error) await notifyOrderParties(supabase, botToken, orderNo, st);
    } else {
      reply = "请用菜单命令操作，发 /help 查看全部命令";
    }

    // 记录回复 + 回复用户
    await supabase.from("tg_bot_message_log").update({ message_out: reply })
      .eq("tg_user_id", tgUserId).eq("message_in", text);
    if (botToken) {
      await fetch(`https://api.telegram.org/bot${botToken}/sendMessage`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ chat_id: chatId, text: reply }),
      });
    }

    return new Response("{}", { headers: { "Content-Type": "application/json" } });
  } catch (e) {
    return new Response("{}", { headers: { "Content-Type": "application/json" } });
  }
});

async function upsertUser(supabase: any, tgUserId: string, name: string) {
  const { data } = await supabase.from("tg_bot_user").select("tg_user_id").eq("tg_user_id", tgUserId).limit(1);
  if (!data || data.length === 0) {
    await supabase.from("tg_bot_user").insert({
      tg_user_id: tgUserId, user_role: "customer", full_name: name, is_active: true,
    });
  }
}

function genOrderNo(): string {
  const d = new Date();
  const ymd = d.toISOString().slice(0, 10).replace(/-/g, "");
  const rand = Math.floor(1000 + Math.random() * 9000);
  return `TG-${ymd}-${rand}`;
}

// 订单状态变更时推送给对应角色（客户/司机）
async function notifyOrderParties(supabase: any, botToken: string | undefined, orderNo: string, status: string) {
  if (!botToken) return;
  const { data } = await supabase.from("tg_charter_order")
    .select("tg_customer_id, tg_driver_id, order_no").eq("order_no", orderNo).single();
  if (!data) return;
  const targets = [data.tg_customer_id, data.tg_driver_id].filter(Boolean);
  for (const chatId of targets) {
    try {
      await fetch(`https://api.telegram.org/bot${botToken}/sendMessage`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ chat_id: chatId, text: `🔔 订单 ${orderNo} 状态更新为 ${status}` }),
      });
    } catch (_) { /* 忽略单条推送失败 */ }
  }
}
