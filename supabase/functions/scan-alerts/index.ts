// 税务风险扫描 Edge Function
// 检查：凭证缺失、SST 逾期、合规到期、库存异常 → 生成 tax_alert
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (_req) => {
  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );
    const alerts: any[] = [];

    // 1. 销项缺发票 PDF
    const { data: noInvoice } = await supabase.from("sales_invoice_summary")
      .select("id, external_order_id").eq("account_type", "official").is("invoice_pdf_path", null);
    for (const r of noInvoice ?? []) {
      alerts.push({ level: "高危", alert_type: "缺发票", related_doc_id: r.external_order_id, description: "销项单据缺发票 PDF" });
    }

    // 2. 进项缺凭证
    const { data: noReceipt } = await supabase.from("purchase_invoice_summary")
      .select("id, external_purchase_id").eq("account_type", "official").is("receipt_path", null);
    for (const r of noReceipt ?? []) {
      alerts.push({ level: "高危", alert_type: "缺凭证", related_doc_id: r.external_purchase_id, description: "进货单缺凭证附件" });
    }

    // 3. 合规日历到期
    const today = new Date();
    const soon = new Date(today.getTime() + 30 * 24 * 3600 * 1000).toISOString().slice(0, 10);
    const { data: dueReminders } = await supabase.from("compliance_reminder")
      .select("id, reminder_type, due_date").lte("due_date", soon).eq("status", "待处理");
    for (const r of dueReminders ?? []) {
      alerts.push({ level: "普通", alert_type: "合规到期", related_doc_id: r.id, description: `${r.reminder_type} 即将/已经到期 (${r.due_date})` });
    }

    // 4. 库存异常（负库存）
    const { data: negStock } = await supabase.from("inventory_summary")
      .select("id, item_name, quantity").lt("quantity", 0);
    for (const r of negStock ?? []) {
      alerts.push({ level: "普通", alert_type: "库存异常", related_doc_id: r.id, description: `商品 ${r.item_name} 出现负库存` });
    }

    // 写入告警
    let inserted = 0;
    for (const a of alerts) {
      const { error } = await supabase.from("tax_alert").insert(a);
      if (!error) inserted++;
    }

    return new Response(
      JSON.stringify({ ok: true, scanned: alerts.length, inserted }),
      { headers: { "Content-Type": "application/json" } },
    );
  } catch (e) {
    return new Response(JSON.stringify({ error: e.message }), {
      status: 400,
      headers: { "Content-Type": "application/json" },
    });
  }
});
