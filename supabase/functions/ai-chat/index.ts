// AI 经营助手 Edge Function（接 DeepSeek，读经营数据生成回答，不修改任何数据）
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  try {
    const { question } = await req.json();
    const apiKey = Deno.env.get("DEEPSEEK_API_KEY");
    if (!apiKey) throw new Error("缺少 DeepSeek API Key");

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    // 读经营数据汇总（报税账口径，不读 owner_drawing）
    const { data: entities } = await supabase.from("business_entity").select("id,name,business_industry");
    let context = "集团经营数据（报税口径）：\n";
    for (const e of entities ?? []) {
      const rev = await supabase.from("cashflow_business").select("amount")
        .eq("business_entity_id", e.id).eq("flow_type", "收入").eq("account_type", "official");
      const cost = await supabase.from("cashflow_business").select("amount")
        .eq("business_entity_id", e.id).eq("flow_type", "成本支出").eq("account_type", "official");
      const inv = await supabase.from("sales_invoice_summary").select("total_amount")
        .eq("business_entity_id", e.id);
      const totalRev = (rev.data ?? []).reduce((s, r) => s + Number(r.amount || 0), 0);
      const totalCost = (cost.data ?? []).reduce((s, r) => s + Number(r.amount || 0), 0);
      const totalInv = (inv.data ?? []).reduce((s, r) => s + Number(r.total_amount || 0), 0);
      context += `- ${e.name}（${e.business_industry ?? ""}）：合规营收 RM${totalRev.toFixed(2)}，成本 RM${totalCost.toFixed(2)}，利润 RM${(totalRev - totalCost).toFixed(2)}，销项合计 RM${totalInv.toFixed(2)}\n`;
    }

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
            content: "你是星域集团经营助手（小聪秘书）。根据提供的经营数据回答用户问题，只读不改数据。用简洁中文，金额保留两位小数，单位 RM。",
          },
          { role: "user", content: context },
          { role: "user", content: question },
        ],
        temperature: 0.7,
        max_tokens: 1200,
      }),
    });

    const data = await resp.json();
    const answer = data.choices?.[0]?.message?.content || "AI 暂时无法回答，请稍后再试";

    return new Response(answer, { headers: { "Content-Type": "text/plain; charset=utf-8" } });
  } catch (e) {
    return new Response("AI 服务异常：" + e.message, {
      status: 400,
      headers: { "Content-Type": "text/plain; charset=utf-8" },
    });
  }
});
