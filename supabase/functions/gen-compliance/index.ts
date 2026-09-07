// 合规日历自动生成 Edge Function（生成马来西亚法定截止日期提醒）
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (_req) => {
  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    const { data: entities } = await supabase.from("business_entity").select("id,name");
    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth() + 1;

    const reminders: any[] = [];
    for (const e of entities ?? []) {
      // 每月 EPF/SOCSO/EIS/PCB 缴款（次月 15 日前）
      const payDue = new Date(year, month, 15);
      reminders.push({
        business_entity_id: e.id,
        reminder_type: "EPF/SOCSO/EIS/PCB 缴款",
        due_date: payDue.toISOString().slice(0, 10),
        description: `${e.name} 月度人力缴款截止（次月15日）`,
      });

      // SST-02 双月申报（每双数月底）
      if (month % 2 === 0) {
        const sstDue = new Date(year, month, 28);
        reminders.push({
          business_entity_id: e.id,
          reminder_type: "SST-02 双月申报",
          due_date: sstDue.toISOString().slice(0, 10),
          description: `${e.name} SST 双月申报截止`,
        });
      }

      // 年度 FormB / FormE / SSM 年审
      reminders.push(
        { business_entity_id: e.id, reminder_type: "FormB 年度报税", due_date: `${year}-06-30`, description: `${e.name} FormB 年度生意报税截止` },
        { business_entity_id: e.id, reminder_type: "FormE 年度薪资申报", due_date: `${year}-03-31`, description: `${e.name} FormE 年度薪资申报截止` },
        { business_entity_id: e.id, reminder_type: "SSM 年审", due_date: `${year}-12-31`, description: `${e.name} SSM 年度更新` },
      );
    }

    let inserted = 0;
    for (const r of reminders) {
      const { data: exist } = await supabase.from("compliance_reminder")
        .select("id").eq("business_entity_id", r.business_entity_id)
        .eq("reminder_type", r.reminder_type).eq("due_date", r.due_date).limit(1);
      if (!exist || exist.length === 0) {
        await supabase.from("compliance_reminder").insert(r);
        inserted++;
      }
    }

    return new Response(JSON.stringify({ ok: true, inserted }), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (e) {
    return new Response(JSON.stringify({ error: e.message }), {
      status: 400,
      headers: { "Content-Type": "application/json" },
    });
  }
});
