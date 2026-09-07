// Telegram Bot 发通知（App 告警/提醒推送到董事长 Telegram）
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

serve(async (req) => {
  try {
    const { text, chat_id } = await req.json();
    const botToken = Deno.env.get("TELEGRAM_BOT_TOKEN");
    const defaultChat = Deno.env.get("TELEGRAM_CHAT_ID");
    if (!botToken) throw new Error("缺少 TELEGRAM_BOT_TOKEN");

    const target = chat_id || defaultChat;
    if (!target) throw new Error("缺少 chat_id");

    const resp = await fetch(`https://api.telegram.org/bot${botToken}/sendMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chat_id: target, text: text || "通知" }),
    });
    const data = await resp.json();

    return new Response(JSON.stringify({ ok: true, data }), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (e) {
    return new Response(JSON.stringify({ error: e.message }), {
      status: 400,
      headers: { "Content-Type": "application/json" },
    });
  }
});
