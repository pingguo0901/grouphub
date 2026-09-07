// Telegram Bot 接收消息 Webhook（Telegram 消息实时同步进 phone_msg_sync）
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  try {
    const update = await req.json();
    const msg = update.message || update.edited_message;
    if (!msg || !msg.text) {
      return new Response("{}", { headers: { "Content-Type": "application/json" } });
    }

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const sender = msg.from?.first_name || msg.from?.username || "Telegram";
    const chatTitle = msg.chat?.title || "";
    const summary = String(msg.text).slice(0, 500);

    const s = (summary + " " + sender + " " + chatTitle).toLowerCase();
    let category = "普通沟通";
    if (/(付款|到账|transfer|payment|duitnow|bank|收据|收款)/.test(s)) category = "付款通知";
    else if (/(发票|invoice|tax|sst|lhdn|报税|税)/.test(s)) category = "税务/发票";
    else if (/(对账|supplier|账单|进货|quote)/.test(s)) category = "供应商对账";

    await supabase.from("phone_msg_sync").insert({
      source: "Telegram",
      sender: chatTitle ? `${sender} (${chatTitle})` : sender,
      summary,
      ai_category: category,
      msg_time: new Date((msg.date || Math.floor(Date.now() / 1000)) * 1000).toISOString(),
    });

    return new Response("{}", { headers: { "Content-Type": "application/json" } });
  } catch (e) {
    return new Response("{}", { headers: { "Content-Type": "application/json" } });
  }
});
