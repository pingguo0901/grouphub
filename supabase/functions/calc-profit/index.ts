// 内部真实利润口径 Edge Function（仅内部管理视图使用）
// 在报税净利润基础上扣除 owner_drawing，得到留存周转金；不输出任何报税文件
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  try {
    const { business_entity_id } = await req.json();
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    // 合规营收（报税账）
    const revQ = supabase
      .from("cashflow_business")
      .select("amount")
      .eq("account_type", "official")
      .eq("flow_type", "收入");
    if (business_entity_id) revQ.eq("business_entity_id", business_entity_id);

    // 可抵扣成本（报税账）
    const costQ = supabase
      .from("cashflow_business")
      .select("amount")
      .eq("account_type", "official")
      .eq("flow_type", "成本支出")
      .eq("deductible", true);
    if (business_entity_id) costQ.eq("business_entity_id", business_entity_id);

    // 人力成本
    const payQ = supabase
      .from("staff_payroll")
      .select("total_cost")
      .eq("account_type", "official");
    if (business_entity_id) payQ.eq("business_entity_id", business_entity_id);

    // 老板提款（真实账）
    const drawQ = supabase.from("owner_drawing").select("amount_myr");
    if (business_entity_id) drawQ.eq("business_entity_id", business_entity_id);

    const [rev, cost, pay, draw] = await Promise.all([revQ, costQ, payQ, drawQ]);

    const revenue = sum(rev.data ?? [], "amount");
    const costTotal = sum(cost.data ?? [], "amount") + sum(pay.data ?? [], "total_cost");
    const taxableProfit = revenue - costTotal;
    const ownerDrawing = sum(draw.data ?? [], "amount_myr");
    const retainedCash = taxableProfit - ownerDrawing;

    return new Response(
      JSON.stringify({
        taxable_profit: round2(taxableProfit),
        owner_drawing_total: round2(ownerDrawing),
        retained_cash: round2(retainedCash),
      }),
      { headers: { "Content-Type": "application/json" } },
    );
  } catch (e) {
    return new Response(JSON.stringify({ error: e.message }), {
      status: 400,
      headers: { "Content-Type": "application/json" },
    });
  }
});

function sum(arr: any[], key: string): number {
  return arr.reduce((t, x) => t + (Number(x[key]) || 0), 0);
}
function round2(n: number): number {
  return Math.round(n * 100) / 100;
}
