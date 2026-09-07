// 工资计算 Edge Function（纯计算，无数据库写入）
// 输入工资项 → 输出完整工资单（EPF/SOCSO/EIS/PCB/净工资/人力总成本）
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { calcEPF, calcSOCSO, calcEIS, calcPCB } from "../_shared/tax-rates.ts";

serve(async (req) => {
  try {
    const b = await req.json();
    const gross =
      (Number(b.basic_salary) || 0) +
      (Number(b.allowance) || 0) +
      (Number(b.overtime) || 0) +
      (Number(b.bonus) || 0);

    const isForeigner = !!b.is_foreigner;
    const ageOver60 = !!b.age_over_60;

    const epf = calcEPF(gross, isForeigner);
    const socso = calcSOCSO(gross, isForeigner, ageOver60);
    const eis = calcEIS(gross, isForeigner);

    // PCB：按年化应税收入估算（gross - 员工EPF）
    const annualTaxable = (gross - epf.employee) * 12;
    const pcb = calcPCB(annualTaxable) / 12;

    const net = gross - epf.employee - socso.employee - eis.employee - pcb;
    const totalCost = gross + epf.employer + socso.employer + eis.employer;

    return new Response(
      JSON.stringify({
        gross_salary: round2(gross),
        employee_epf: round2(epf.employee),
        employer_epf: round2(epf.employer),
        employee_socso: round2(socso.employee),
        employer_socso: round2(socso.employer),
        employee_eis: round2(eis.employee),
        employer_eis: round2(eis.employer),
        pcb_mtd: round2(pcb),
        net_salary: round2(net),
        total_cost: round2(totalCost),
      }),
      { headers: { "Content-Type": "application/json" } },
    );
  } catch (e) {
    return new Response(
      JSON.stringify({ error: e.message }),
      { status: 400, headers: { "Content-Type": "application/json" } },
    );
  }
});

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}
