import imaplib, email, json, os, urllib.request
from email.header import decode_header
from datetime import datetime, timedelta

GMAIL_EMAIL = "pingguo0901@gmail.com"
GMAIL_APP_PASSWORD = "kihtnedscvqjlurh"
SUPABASE_URL = "https://acvaccrjjxaeuulnpkhz.supabase.co"
STATE_FILE = os.path.expanduser("~/.openclaw/workspace/gmail_last_sync.txt")

def get_env(key):
    for line in open(os.path.expanduser("~/.openclaw/workspace/.env")):
        if line.startswith(key + "="):
            return line[len(key)+1:].strip()
    return ""

SRK = get_env("GROUPHUB" + "_SUPABASE_SERVICE_ROLE_KEY")

def dec(s):
    if s is None: return ""
    out = []
    for txt, enc in decode_header(s):
        if isinstance(txt, bytes): out.append(txt.decode(enc or "utf-8", errors="replace"))
        else: out.append(txt)
    return "".join(out)

def classify(subject, sender):
    s = (subject + " " + sender).lower()
    if any(k in s for k in ["invoice", "receipt", "发票", "tax", "lhdn", "sst", "form"]): return "税务/发票"
    if any(k in s for k in ["payment", "paid", "付款", "到账", "duitnow", "transfer", "bank"]): return "付款通知"
    if any(k in s for k in ["supplier", "对账", "statement", "bill", "账单"]): return "供应商对账"
    return "普通沟通"

# 只读这些来源的邮件（发件人白名单）
WHITELIST_KEYWORDS = [
    "facebook", "facebookmail", "meta",
    "google", "accounts.google",
    "bank", "gxbank", "maybank", "cimb", "public bank", "hong leong", "rhb", "ambank", "alliance bank", "bank negara",
    "tencent", "qcloud", "cloud.tencent",
    "tng", "touchngo", "touch 'n go", "touch n go",
]

def should_sync(sender):
    s = sender.lower()
    return any(k in s for k in WHITELIST_KEYWORDS)

def insert_msg(source, msg_time, sender, subject, category, msg_id):
    data = json.dumps([{
        "source": source,
        "msg_time": msg_time,
        "sender": sender,
        "summary": subject[:200],
        "ai_category": category,
        "linked_doc_id": msg_id,
    }]).encode()
    req = urllib.request.Request(
        SUPABASE_URL + "/rest/v1/phone_msg_sync",
        data=data,
        headers={
            "apikey": SRK, "Authorization": "Bearer " + SRK,
            "Content-Type": "application/json", "Prefer": "return=minimal",
        },
        method="POST",
    )
    try:
        urllib.request.urlopen(req)
        return True
    except Exception:
        return False

# 上次同步时间（状态文件），无则取最近 24 小时
last = None
if os.path.exists(STATE_FILE):
    last = open(STATE_FILE).read().strip()

mail = imaplib.IMAP4_SSL("imap.gmail.com", 993)
mail.login(GMAIL_EMAIL, GMAIL_APP_PASSWORD)
mail.select("inbox")

if last:
    since = datetime.fromisoformat(last).strftime("%d-%b-%Y")
else:
    since = (datetime.now() - timedelta(hours=24)).strftime("%d-%b-%Y")

status, data = mail.search(None, f'(SINCE "{since}")')
ids = data[0].split()

count = 0
for mid in ids[-200:]:
    status, msg_data = mail.fetch(mid, "(RFC822)")
    msg = email.message_from_bytes(msg_data[0][1])
    subject = dec(msg["Subject"])
    sender = dec(msg["From"])
    if not should_sync(sender):
        continue
    msg_id = msg["Message-ID"] or mid.decode()
    try:
        dt = email.utils.parsedate_to_datetime(msg["Date"])
        msg_time = dt.isoformat()
    except Exception:
        msg_time = datetime.now().isoformat()
    if insert_msg("Gmail", msg_time, sender, subject, classify(subject, sender), msg_id):
        count += 1

mail.logout()
open(STATE_FILE, "w").write(datetime.now().isoformat())
print(f"Gmail 同步完成：{count} 封新邮件")
