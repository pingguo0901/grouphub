// AI 经营助手 Edge Function（接 DeepSeek，只读生成回答，不修改任何数据）
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

serve(async (req) => {
  try {
    const { question } = await req.json();
    const apiKey = Deno.env.get("DEEPSEEK_API_KEY");
    if (!apiKey) throw new Error("缺少 DeepSeek API Key");

    const resp = await fetch("https://api.deepseek.com/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${apiKey}`,
      },
      body: JSON.stringify({
        model: "deepseek-chat",
        messages: [
          {
            role: "system",
            content: "你是星域集团经营助手（小聪秘书）。只根据用户问题回答经营相关问题，不修改任何财务/提款/报税数据。用简洁中文回答。",
          },
          { role: "user", content: question },
        ],
        temperature: 0.7,
        max_tokens: 1000,
      }),
    });

    const data = await resp.json();
    const answer = data.choices?.[0]?.message?.content || "AI 暂时无法回答，请稍后再试";

    // 返回纯文本，方便前端直接展示
    return new Response(answer, { headers: { "Content-Type": "text/plain; charset=utf-8" } });
  } catch (e) {
    return new Response("AI 服务异常：" + e.message, {
      status: 400,
      headers: { "Content-Type": "text/plain; charset=utf-8" },
    });
  }
});
