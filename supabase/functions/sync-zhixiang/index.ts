// 同步炙巷食铺数据 → group-hub（营收/成本/进货/库存/员工薪资）
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (_req) => {
  try {
    // 目标：group-hub（自动注入）
    const target = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );
    // 源：炙巷食铺
    const src = createClient(
      Deno.env.get("ZXSP_URL")!,
      Deno.env.get("ZXSP_SERVICE_ROLE")!,
    );

    const { data: entity } = await target.from("business_entity")
      .select("id").eq("business_industry", "F&B").single();
    if (!entity) throw new Error("找不到炙巷食铺主体");
    const entityId = entity.id;

    // 读源数据
    const { data: receipts } = await src.from("receipt_master").select("*").order("trans_datetime", { ascending: false }).limit(1000);
    const { data: expenses } = await src.from("expense_records").select("*").limit(1000);
    const { data: stockIns } = await src.from("stock_in_log").select("*").limit(1000);
    const { data: items } = await src.from("warehouse_items").select("*").limit(5000);
    const { data: staff } = await src.from("staff").select("*").limit(500);

    // 物品 id → 名称映射
    const itemName = new Map<number, string>();
    for (const it of items ?? []) itemName.set(Number(it.id), String(it.item_name ?? ""));

    // 清空旧同步数据（该主体）
    await target.from("sales_invoice_summary").delete().eq("business_entity_id", entityId);
    await target.from("cashflow_business").delete().eq("business_entity_id", entityId);
    await target.from("purchase_invoice_summary").delete().eq("business_entity_id", entityId);
    await target.from("inventory_summary").delete().eq("business_entity_id", entityId);
    await target.from("staff_payroll").delete().eq("business_entity_id", entityId);

    let invoiceCount = 0, costCount = 0, purchaseCount = 0, inventoryCount = 0, payrollCount = 0;

    // 营收 → 销项台账
    for (const r of receipts ?? []) {
      await target.from("sales_invoice_summary").insert({
        business_entity_id: entityId,
        account_type: "official",
        business_industry: "F&B",
        external_order_id: String(r.receipt_no ?? ""),
        doc_type: "销售订单",
        order_date: String(r.trans_datetime ?? "").slice(0, 10),
        net_amount: Number(r.sub_total) || 0,
        sst_amount: 0,
        total_amount: Number(r.total_amount) || 0,
        customer_info: r.payment_mode,
        invoice_status: "待验证",
      });
      invoiceCount++;
    }

    // 成本 → 收支总账
    for (const e of expenses ?? []) {
      if (e.is_personal) continue;
      await target.from("cashflow_business").insert({
        business_entity_id: entityId,
        account_type: "official",
        business_industry: "F&B",
        flow_date: String(e.transaction_datetime ?? "").slice(0, 10),
        flow_type: "成本支出",
        category: e.expense_type,
        amount: Number(e.amount_myr) || 0,
        deductible: true,
        external_doc_id: String(e.id ?? ""),
        receipt_path: e.receipt_invoice_no,
      });
      costCount++;
    }

    // 进货 → 进项台账 + 库存台账（inventory_summary）
    for (const s of stockIns ?? []) {
      await target.from("purchase_invoice_summary").insert({
        business_entity_id: entityId,
        account_type: "official",
        business_industry: "F&B",
        external_purchase_id: String(s.stock_in_no ?? ""),
        purchase_date: String(s.transaction_datetime ?? "").slice(0, 10),
        total_amount: Number(s.total_cost_myr) || 0,
        input_sst_amount: 0,
        sst_type: "可抵扣",
        stock_status: "已入库",
      });
      purchaseCount++;

      // 展开进货明细 → 库存台账
      for (const it of s.in_items ?? []) {
        const qty = Number(it.qty) || 0;
        const unit = Number(it.unit_price) || 0;
        await target.from("inventory_summary").insert({
          business_entity_id: entityId,
          account_type: "official",
          business_industry: "F&B",
          item_name: itemName.get(Number(it.warehouse_item_id)) ?? String(it.warehouse_item_id ?? ""),
          movement_type: "入库",
          quantity: qty,
          unit_cost: unit,
          total_amount: round2(qty * unit),
          external_stock_id: String(s.stock_in_no ?? ""),
        });
        inventoryCount++;
      }
    }

    // 员工 → 薪资台账（占位薪资，待董事长确认真实金额）
    const payrollMonth = "2026-08";
    for (const st of staff ?? []) {
      const isBoss = String(st.role ?? "").toLowerCase() === "admin";
      const basic = isBoss ? 4000 : 2000;
      await target.from("staff_payroll").insert({
        business_entity_id: entityId,
        account_type: "official",
        business_industry: "F&B",
        payroll_month: payrollMonth,
        staff_name: String(st.staff_name ?? ""),
        basic_salary: basic,
        allowance: 0,
        overtime: 0,
        bonus: 0,
        gross_salary: basic,
        employee_epf: 0,
        employer_epf: 0,
        employee_socso: 0,
        employer_socso: 0,
        employee_eis: 0,
        employer_eis: 0,
        pcb_mtd: 0,
        net_salary: basic,
        total_cost: basic,
        is_foreigner: false,
        external_payroll_id: String(st.id ?? ""),
      });
      payrollCount++;
    }

    // 记录同步状态
    await target.from("biz_app_sync").insert({
      business_entity_id: entityId,
      sync_type: "炙巷食铺全量同步",
      last_success_at: new Date().toISOString(),
      status: "成功",
      row_count: invoiceCount + costCount + purchaseCount + inventoryCount + payrollCount,
    });

    return new Response(JSON.stringify({
      ok: true, invoice: invoiceCount, cost: costCount, purchase: purchaseCount,
      inventory: inventoryCount, payroll: payrollCount,
    }), { headers: { "Content-Type": "application/json" } });
  } catch (e) {
    return new Response(JSON.stringify({ error: e.message }), {
      status: 400, headers: { "Content-Type": "application/json" },
    });
  }
});

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}
