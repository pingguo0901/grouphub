// 马来西亚 2026 最新社保税务参数（供所有 Edge Functions 引用）
// ⚠️ 上线前需会计师最终复核确认

// EPF 公积金
export const EPF = {
  employeeRate: 0.11,        // 员工 11%
  employerRateLow: 0.13,     // 雇主 13%（月薪 ≤ RM5,000）
  employerRateHigh: 0.12,    // 雇主 12%（月薪 > RM5,000）
  threshold: 5000,           // 分界点 RM5,000
  foreignEmployerRate: 0.02, // 外籍雇主 2%
  foreignEmployeeRate: 0.02, // 外籍员工 2%
};

// SOCSO 社会保障（第一类：工伤+残疾）
export const SOCSO = {
  employerRate: 0.0175,       // 雇主 1.75%
  employeeRate: 0.005,        // 员工 0.5%
  ceiling: 6000,              // 缴费基数上限 RM6,000
  seniorEmployerRate: 0.0125, // 60岁以上：员工 0，雇主 1.25%
  foreignEmployerRate: 0.0125,// 外籍（第二类仅工伤）：雇主 1.25%，员工 0
};

// EIS 就业保险（仅马来西亚公民，外籍关闭）
export const EIS = {
  employerRate: 0.002, // 0.2%
  employeeRate: 0.002, // 0.2%
  ceiling: 6000,       // 上限 RM6,000
};

// HRDF 人力资源发展基金（<10 人豁免）
export const HRDF = {
  rate10plus: 0.01,   // 10 人以上 1%
  rate5to9: 0.005,    // 5-9 人 0.5%（可选）
  exemptThreshold: 10, // 少于 10 名本地员工豁免
};

// PCB/MTD 个人所得税累进税率（年度）
export const PCB_BRACKETS = [
  { from: 0, to: 5000, rate: 0.0 },
  { from: 5000, to: 20000, rate: 0.01 },
  { from: 20000, to: 35000, rate: 0.03 },
  { from: 35000, to: 50000, rate: 0.06 },
  { from: 50000, to: 70000, rate: 0.11 },
  { from: 70000, to: 100000, rate: 0.19 },
  { from: 100000, to: 400000, rate: 0.25 },
  { from: 400000, to: 600000, rate: 0.26 },
  { from: 600000, to: 2000000, rate: 0.28 },
  { from: 2000000, to: Infinity, rate: 0.3 },
];

// SST 销售与服务税
export const SST = {
  salesTax: 0.1,          // 销售税 10%（部分商品 5%）
  serviceTax: 0.08,       // 服务税 8%（2024-03 起，多数服务）
  foodServiceTax: 0.06,   // 餐饮服务税 6%
};

// 公司所得税（中小企业分档，用于参考）
export const CORPORATE_TAX = {
  standard: 0.24,           // 标准 24%
  smeFirst150k: 0.15,       // 中小企业首 RM150,000：15%
  smeNext450k: 0.17,        // 中小企业 RM150,001 - RM600,000：17%
};

// 计算 PCB（简化年度累进，按月折算计税收入）
export function calcPCB(annualTaxableIncome: number): number {
  let tax = 0;
  for (const b of PCB_BRACKETS) {
    if (annualTaxableIncome <= b.from) break;
    const upper = Math.min(annualTaxableIncome, b.to);
    tax += (upper - b.from) * b.rate;
  }
  return tax;
}

// 计算 EPF（返回雇主、员工）
export function calcEPF(gross: number, isForeigner: boolean) {
  if (isForeigner) {
    return {
      employer: gross * EPF.foreignEmployerRate,
      employee: gross * EPF.foreignEmployeeRate,
    };
  }
  const employerRate = gross <= EPF.threshold ? EPF.employerRateLow : EPF.employerRateHigh;
  return {
    employer: gross * employerRate,
    employee: gross * EPF.employeeRate,
  };
}

// 计算 SOCSO（返回雇主、员工）
export function calcSOCSO(gross: number, isForeigner: boolean, ageOver60: boolean) {
  const base = Math.min(gross, SOCSO.ceiling);
  if (isForeigner || ageOver60) {
    return { employer: base * SOCSO.seniorEmployerRate, employee: 0 };
  }
  return { employer: base * SOCSO.employerRate, employee: base * SOCSO.employeeRate };
}

// 计算 EIS（外籍关闭）
export function calcEIS(gross: number, isForeigner: boolean) {
  if (isForeigner) return { employer: 0, employee: 0 };
  const base = Math.min(gross, EIS.ceiling);
  return { employer: base * EIS.employerRate, employee: base * EIS.employeeRate };
}
