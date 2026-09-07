// 同步星域臻旅数据 → group-hub（真实包车经营账，account_type=private）
// ⚠️ 红线：包车数据只进真实经营账(private)，绝不进报税账(official)
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (_req) => {
  try {
    const target = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );
    const src = createClient(
      Deno.env.get("XYZL_URL")!,
      Deno.env.get("XYZL_SERVICE_ROLE")!,
    );

    const { data: entity } = await target.from("business_entity")
      .select("id").eq("business_industry", "ElectronicTrade").single();
    if (!entity) throw new Error("找不到星域臻旅主体");
    const entityId = entity.id;

    // 财务流水（收入/支出）→ 真实经营账
    const { data: flows } = await src.from("finance_transactions").select("*").limit(2000);
    // 司机支出 → 成本
    const { data: driverExp } = await src.from("driver_expenses").select("*").limit(2000);

    // 清空该主体 private 账旧数据
    await target.from("cashflow_business").delete()
      .eq("business_entity_id", entityId).eq("account_type", "private");

    let incomeCount = 0, costCount = 0;

    for (const f of flows ?? []) {
      const isIncome = String(f.income_outcome ?? "").includes("收入") || String(f.income_outcome ?? "").toLowerCase().includes("income");
      await target.from("cashflow_business").insert({
        business_entity_id: entityId,
        account_type: "private",
        business_industry: "ElectronicTrade",
        flow_date: String(f.settle_time ?? f.created_at ?? "").slice(0, 10),
        flow_type: isIncome ? "收入" : "成本支出",
        category: String(f.relate_type ?? "") || "包车流水",
        amount: Number(f.actual_amount ?? f.amount) || 0,
        deductible: false,
        external_doc_id: String(f.order_no ?? ""),
      });
      if (isIncome) incomeCount++; else costCount++;
    }

    for (const d of driverExp ?? []) {
      await target.from("cashflow_business").insert({
        business_entity_id: entityId,
        account_type: "private",
        business_industry: "ElectronicTrade",
        flow_date: String(d.express_date ?? "").slice(0, 10),
        flow_type: "成本支出",
        category: String(d.type ?? "") || "司机支出",
        amount: Number(d.express_amount) || 0,
        deductible: false,
        external_doc_id: String(d.uuid ?? ""),
        receipt_path: d.receipt_url,
      });
      costCount++;
    }

    await target.from("biz_app_sync").insert({
      business_entity_id: entityId,
      sync_type: "星域臻旅真实账同步",
      last_success_at: new Date().toISOString(),
      status: "成功",
      row_count: incomeCount + costCount,
    });

    return new Response(JSON.stringify({
      ok: true, income: incomeCount, cost: costCount,
    }), { headers: { "Content-Type": "application/json" } });
  } catch (e) {
    return new Response(JSON.stringify({ error: e.message }), {
      status: 400, headers: { "Content-Type": "application/json" },
    });
  }
});
