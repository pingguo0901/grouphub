// 工资单生成 Edge Function：生成工资单 CSV + 人力缴款月报，归档到 biz_doc_archive
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  try {
    const { payroll_month } = await req.json();
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    let q = supabase.from("staff_payroll").select("*").eq("account_type", "official");
    if (payroll_month) q = q.eq("payroll_month", payroll_month);
    const { data: rows } = await q;

    if (!rows || rows.length === 0) {
      return new Response(JSON.stringify({ ok: false, msg: "无薪资数据" }), {
        headers: { "Content-Type": "application/json" },
      });
    }

    // 生成 CSV（UTF-8 带 BOM，Excel 可识别中文）
    const header = "员工,底薪,津贴,加班,奖金,毛工资,员工EPF,雇主EPF,员工SOCSO,雇主SOCSO,员工EIS,雇主EIS,PCB,实发,人力总成本";
    const lines = rows.map((r) =>
      [
        r.staff_name, r.basic_salary, r.allowance, r.overtime, r.bonus, r.gross_salary,
        r.employee_epf, r.employer_epf, r.employee_socso, r.employer_socso,
        r.employee_eis, r.employer_eis, r.pcb_mtd, r.net_salary, r.total_cost,
      ].join(",")
    );
    const csv = "\uFEFF" + [header, ...lines].join("\n");

    // 汇总
    const totalEpf = rows.reduce((s, r) => s + Number(r.employer_epf || 0), 0);
    const totalSocso = rows.reduce((s, r) => s + Number(r.employer_socso || 0), 0);
    const totalEis = rows.reduce((s, r) => s + Number(r.employer_eis || 0), 0);
    const totalPcb = rows.reduce((s, r) => s + Number(r.pcb_mtd || 0), 0);
    const totalCost = rows.reduce((s, r) => s + Number(r.total_cost || 0), 0);
    // HRDF：本地员工 >= 10 人缴 1% 工资总额，<10 人豁免
    const hrdf = rows.length >= 10 ? totalCost * 0.01 : 0;

    // 归档到 biz_doc_archive
    const month = payroll_month || new Date().toISOString().slice(0, 7);
    const path = `payslips/payslip_${month}.csv`;
    await supabase.storage.from("biz_doc_archive").upload(path, csv, {
      contentType: "text/csv",
      upsert: true,
    });

    // 生成 HTML 工资单（中文正常，可打印成 PDF）
    const htmlRows = rows.map((r) =>
      `<tr><td>${r.staff_name}</td><td>${r.basic_salary}</td><td>${r.gross_salary}</td><td>${r.employee_epf}</td><td>${r.employer_epf}</td><td>${r.employee_socso}</td><td>${r.employer_socso}</td><td>${r.employee_eis}</td><td>${r.employer_eis}</td><td>${r.pcb_mtd}</td><td>${r.net_salary}</td><td>${r.total_cost}</td></tr>`
    ).join("");
    const html = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>工资单 ${month}</title></head><body><h1>工资单 ${month}</h1><table border="1" cellpadding="6"><tr><th>员工</th><th>底薪</th><th>毛工资</th><th>员工EPF</th><th>雇主EPF</th><th>员工SOCSO</th><th>雇主SOCSO</th><th>员工EIS</th><th>雇主EIS</th><th>PCB</th><th>实发</th><th>人力总成本</th></tr>${htmlRows}</table><p>汇总：雇主EPF RM${totalEpf} · 雇主SOCSO RM${totalSocso} · 雇主EIS RM${totalEis} · PCB RM${totalPcb} · 人力总成本 RM${totalCost} · HRDF RM${Math.round(hrdf * 100) / 100}</p></body></html>`;
    await supabase.storage.from("biz_doc_archive").upload(`payslips/payslip_${month}.html`, html, {
      contentType: "text/html", upsert: true,
    });

    await supabase.from("audit_log").insert({
      action_type: "生成工资单",
      related_table_id: path,
      description: `生成工资单 ${month} 并归档`,
    });

    return new Response(
      JSON.stringify({
        ok: true,
        path,
        rows: rows.length,
        total_epf: totalEpf, total_socso: totalSocso, total_eis: totalEis,
        total_pcb: totalPcb, total_cost: totalCost, hrdf: Math.round(hrdf * 100) / 100,
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
