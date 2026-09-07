// 归档文档 Edge Function：上传 PDF/凭证到对应 Storage Bucket，并写审计日志
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  try {
    const { bucket, path, content, text, content_type } = await req.json();
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    let bytes: Uint8Array;
    if (content) {
      bytes = Uint8Array.from(atob(content), (c) => c.charCodeAt(0));
    } else if (text) {
      bytes = new TextEncoder().encode(text);
    } else {
      throw new Error("缺少内容");
    }

    const { error } = await supabase.storage.from(bucket).upload(path, bytes, {
      contentType: content_type || "application/pdf",
      upsert: true,
    });
    if (error) throw new Error(error.message);

    await supabase.from("audit_log").insert({
      action_type: "归档文档",
      related_table_id: path,
      description: `归档文件到 ${bucket}`,
    });

    return new Response(JSON.stringify({ ok: true, path }), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (e) {
    return new Response(JSON.stringify({ error: e.message }), {
      status: 400,
      headers: { "Content-Type": "application/json" },
    });
  }
});
