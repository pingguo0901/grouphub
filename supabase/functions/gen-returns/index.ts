// 报税底稿生成 Edge Function
// 生成 SST02（每主体）、FormB（两家合并）、FormE（薪资年度汇总）、CP500 分期
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  try {
    const { tax_period, tax_year } = await req.json();
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    // 两家主体
    const { data: entities } = await supabase.from("business_entity").select("id, name");

    // 合并应税利润
    let zhixiangProfit = 0;
    let xyzlProfit = 0;

    for (const e of entities ?? []) {
      // 该主体报税口径计算
      const sales = await supabase.from("sales_invoice_summary")
        .select("sst_amount, net_amount").eq("business_entity_id", e.id).eq("account_type", "official");
      const purchase = await supabase.from("purchase_invoice_summary")
        .select("input_sst_amount, sst_type, total_amount").eq("business_entity_id", e.id).eq("account_type", "official");
      const cashflow = await supabase.from("cashflow_business")
        .select("flow_type, amount, deductible").eq("business_entity_id", e.id).eq("account_type", "official");
      const payroll = await supabase.from("staff_payroll")
        .select("total_cost").eq("business_entity_id", e.id).eq("account_type", "official");

      const outputSST = sum(sales.data ?? [], "sst_amount");
      const inputSST = sum((purchase.data ?? []).filter((x) => x.sst_type === "可抵扣"), "input_sst_amount");
      const sstPayable = outputSST - inputSST;

      // 写入 SST02 底稿
      await supabase.from("sst_return").insert({
        business_entity_id: e.id,
        tax_period: tax_period,
        output_sst: outputSST,
        input_sst_deductible: inputSST,
        sst_payable: sstPayable,
        draft_generated_at: new Date().toISOString(),
        filing_status: "草稿",
      });

      // 应税利润
      const revenue = sum((cashflow.data ?? []).filter((x) => x.flow_type === "收入"), "amount");
      const cost = sum((cashflow.data ?? []).filter((x) => x.flow_type === "成本支出" && x.deductible), "amount") +
        sum(payroll.data ?? [], "total_cost");
      const profit = revenue - cost;

      if (e.name?.includes("炙巷")) zhixiangProfit = profit;
      else xyzlProfit = profit;
    }

    // 写入 FormB 合并底稿
    const totalProfit = zhixiangProfit + xyzlProfit;
    await supabase.from("formb_tax").insert({
      tax_year: tax_year,
      zhixiang_profit: zhixiangProfit,
      xyzl_profit: xyzlProfit,
      total_taxable_profit: totalProfit,
      draft_generated_at: new Date().toISOString(),
      filing_status: "草稿",
    });

    // FormE 薪资年度汇总
    for (const e of entities ?? []) {
      const pay = await supabase.from("staff_payroll")
        .select("employer_epf, employer_socso, employer_eis, pcb_mtd")
        .eq("business_entity_id", e.id).eq("account_type", "official");
      const rows = pay.data ?? [];
      await supabase.from("forme_submit").insert({
        business_entity_id: e.id,
        tax_year: tax_year,
        total_epf: sum(rows, "employer_epf"),
        total_socso: sum(rows, "employer_socso"),
        total_eis: sum(rows, "employer_eis"),
        total_pcb: sum(rows, "pcb_mtd"),
        draft_generated_at: new Date().toISOString(),
        filing_status: "草稿",
      });
    }

    return new Response(
      JSON.stringify({ ok: true, zhixiang_profit: round2(zhixiangProfit), xyzl_profit: round2(xyzlProfit), total_taxable_profit: round2(totalProfit) }),
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
