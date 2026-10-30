const crypto = require("crypto");

async function main() {
  const { publicKey } = crypto.generateKeyPairSync("x25519");
  const raw = publicKey.export({ type: "spki", format: "der" }).slice(-32);
  const pubB64 = Buffer.from(raw).toString("base64");
  const body = {
    key: pubB64,
    install_id: "",
    fcm_token: "",
    tos: new Date().toISOString(),
    model: "PC",
    serial_number: "",
    locale: "en_US"
  };
  const r = await fetch("https://api.cloudflareclient.com/v0a2158/reg", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "CF-Client-Version": "a-6.30-2158",
      "User-Agent": "okhttp/3.12.1"
    },
    body: JSON.stringify(body)
  });
  const t = await r.text();
  console.log("status", r.status, "len", t.length);
  console.log(t.slice(0, 900));
}
main().catch(e => console.log("ERR", e.message));
