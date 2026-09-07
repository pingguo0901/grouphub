// 手机通知消息上传 Edge Function（接收 WA/WeChat 通知，写 phone_msg_sync）
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  try {
    const { source, sender, summary, msg_time } = await req.json();
    if (!source || !sender || !summary) throw new Error("缺少参数");

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    // AI 初步分类
    const s = (String(sender) + " " + String(summary)).toLowerCase();
    let category = "普通沟通";
    if (/(付款|到账|transfer|payment|duitnow|bank|收据|收款)/.test(s)) category = "付款通知";
    else if (/(发票|invoice|tax|sst|lhdn|报税|税)/.test(s)) category = "税务/发票";
    else if (/(对账|supplier|账单|进货|quote)/.test(s)) category = "供应商对账";

    const { error } = await supabase.from("phone_msg_sync").insert({
      source,
      sender,
      summary: String(summary).slice(0, 500),
      ai_category: category,
      msg_time: msg_time ? new Date(msg_time).toISOString() : new Date().toISOString(),
    });
    if (error) throw new Error(error.message);

    return new Response(JSON.stringify({ ok: true }), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (e) {
    return new Response(JSON.stringify({ error: e.message }), {
      status: 400,
      headers: { "Content-Type": "application/json" },
    });
  }
});
