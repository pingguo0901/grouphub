// LHDN 报税口径计算 Edge Function
// ⚠️ 红线：本 function 绝不读取 owner_drawing 表
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  try {
    const { business_entity_id, period } = await req.json();
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    // 销项（报税账）
    const salesQ = supabase
      .from("sales_invoice_summary")
      .select("net_amount, sst_amount, total_amount")
      .eq("account_type", "official");
    if (business_entity_id) salesQ.eq("business_entity_id", business_entity_id);

    // 进项（报税账，可抵扣）
    const purchaseQ = supabase
      .from("purchase_invoice_summary")
      .select("total_amount, input_sst_amount, sst_type")
      .eq("account_type", "official");
    if (business_entity_id) purchaseQ.eq("business_entity_id", business_entity_id);

    // 合规收支（报税账）
    const cashflowQ = supabase
      .from("cashflow_business")
      .select("flow_type, amount, deductible")
      .eq("account_type", "official");
    if (business_entity_id) cashflowQ.eq("business_entity_id", business_entity_id);

    // 人力成本（报税账，可抵扣）
    const payrollQ = supabase
      .from("staff_payroll")
      .select("total_cost")
      .eq("account_type", "official");
    if (business_entity_id) payrollQ.eq("business_entity_id", business_entity_id);

    // 折旧（报税账）
    const assetQ = supabase
      .from("asset_depreciation")
      .select("monthly_depreciation")
      .eq("account_type", "official");
    if (business_entity_id) assetQ.eq("business_entity_id", business_entity_id);

    const [sales, purchase, cashflow, payroll, asset] = await Promise.all([
      salesQ, purchaseQ, cashflowQ, payrollQ, assetQ,
    ]);

    const s = sales.data ?? [];
    const p = purchase.data ?? [];
    const c = cashflow.data ?? [];
    const pay = payroll.data ?? [];
    const a = asset.data ?? [];

    // 销项 SST / 销项营收
    const outputSST = sum(s, "sst_amount");
    const salesRevenue = sum(s, "net_amount");

    // 进项可抵扣 SST
    const inputSST = sum(p.filter((x) => x.sst_type === "可抵扣"), "input_sst_amount");
    const sstPayable = outputSST - inputSST;

    // 营收 / 可抵扣成本
    const revenue = sum(c.filter((x) => x.flow_type === "收入"), "amount");
    const costDeductible =
      sum(c.filter((x) => x.flow_type === "成本支出" && x.deductible), "amount") +
      sum(pay, "total_cost") +
      sum(a, "monthly_depreciation");
    const taxableProfit = revenue - costDeductible;

    return new Response(
      JSON.stringify({
        output_sst: round2(outputSST),
        input_sst_deductible: round2(inputSST),
        sst_payable: round2(sstPayable),
        sales_revenue: round2(salesRevenue),
        revenue: round2(revenue),
        cost_deductible: round2(costDeductible),
        taxable_profit: round2(taxableProfit),
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
