package com.tobi.app.core.runtime

import app.cash.quickjs.QuickJs

class JsEngineException(message: String) : Exception(message)

class JsEngine(private val bridge: NativeBridge) {
    private val quickJs: QuickJs = QuickJs.create()
    private var loaded = false

    fun loadScript(source: String) {
        if (loaded) return
        quickJs.set("__native", NativeBridgeApi::class.java, bridge)
        quickJs.evaluate(BRIDGE_SHIM)
        quickJs.evaluate(transformScript(source))
        loaded = true
    }

    fun invoke(functionName: String, argsJson: String): String {
        val escaped = argsJson
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        val script = "globalThis.__tobiResult = null;\n" +
            "globalThis.__tobiError = null;\n" +
            "try {\n" +
            "  var __tobiArgs = JSON.parse(\"$escaped\");\n" +
            "  var __tobiRet = $functionName.apply(null, __tobiArgs);\n" +
            "  if (__tobiRet !== null && __tobiRet !== undefined && typeof __tobiRet.then === \"function\") {\n" +
            "    __tobiRet.then(function(v) { globalThis.__tobiResult = JSON.stringify(v === undefined ? null : v); }, function(e) { globalThis.__tobiError = String(e && e.message ? e.message : e); });\n" +
            "  } else {\n" +
            "    globalThis.__tobiResult = JSON.stringify(__tobiRet === undefined ? null : __tobiRet);\n" +
            "  }\n" +
            "} catch (e) {\n" +
            "  globalThis.__tobiError = String(e && e.message ? e.message : e);\n" +
            "}\n"
        quickJs.evaluate(script)
        val deadline = System.currentTimeMillis() + 90000
        while (System.currentTimeMillis() < deadline) {
            val error = quickJs.evaluate("globalThis.__tobiError") as? String
            if (error != null) throw JsEngineException(error)
            val result = quickJs.evaluate("globalThis.__tobiResult") as? String
            if (result != null) return result
            quickJs.evaluate("")
            Thread.sleep(10)
        }
        throw JsEngineException("JavaScript execution timed out")
    }

    fun close() {
        try {
            quickJs.close()
        } catch (e: Exception) {
            RuntimeLog.log("warn", "QuickJS close failed: ${e.message}")
        }
    }

    companion object {
        private val BRIDGE_SHIM = """
            var __nativeCall = function(method, args) {
                var raw = __native.invokeNative(method, JSON.stringify(args));
                return JSON.parse(raw);
            };
            globalThis.Bridge = {
                http: {
                    send: function(req) { return __nativeCall("http.send", req); }
                },
                dom: {
                    query: function(html, css) { return __nativeCall("dom.query", { html: html, css: css }); }
                },
                webview: {
                    sniff: function(opts) { return __nativeCall("webview.sniff", opts); }
                },
                crypto: {
                    decrypt: function(opts) { return __nativeCall("crypto.decrypt", opts); },
                    encrypt: function(opts) { return __nativeCall("crypto.encrypt", opts); },
                    hmacSha256: function(opts) { return __nativeCall("crypto.hmacSha256", opts); },
                    sha256: function(text) { return __nativeCall("crypto.sha256", { text: text }); },
                    md5: function(text) { return __nativeCall("crypto.md5", { text: text }); },
                    base64Encode: function(text) { return __nativeCall("crypto.base64Encode", { text: text }); },
                    base64Decode: function(text) { return __nativeCall("crypto.base64Decode", { text: text }); },
                    hexEncode: function(text) { return __nativeCall("crypto.hexEncode", { text: text }); },
                    hexDecode: function(text) { return __nativeCall("crypto.hexDecode", { text: text }); }
                },
                vault: {
                    get: function(key) { return __nativeCall("vault.get", { key: key }); },
                    set: function(key, val) { return __nativeCall("vault.set", { key: key, value: val }); }
                },
                log: function(level, msg) { return __nativeCall("log", { level: level, message: msg }); }
            };
            globalThis.console = {
                log: function() { var parts = []; for (var i = 0; i < arguments.length; i++) { parts.push(String(arguments[i])); } Bridge.log("info", parts.join(" ")); },
                error: function() { var parts = []; for (var i = 0; i < arguments.length; i++) { parts.push(String(arguments[i])); } Bridge.log("error", parts.join(" ")); },
                warn: function() { var parts = []; for (var i = 0; i < arguments.length; i++) { parts.push(String(arguments[i])); } Bridge.log("warn", parts.join(" ")); }
            };
            globalThis.__tobiAll = function(values) { return values; };
            globalThis.fetch = function(url, opts) {
                var o = opts || {};
                var res = Bridge.http.send({ method: o.method || "GET", url: String(url), headers: o.headers || {}, body: o.body || null });
                return {
                    ok: res.status >= 200 && res.status < 300,
                    status: res.status,
                    url: res.finalUrl,
                    text: function() { return res.bodyText; },
                    json: function() { return JSON.parse(res.bodyText); },
                    headers: { get: function(n) { return res.headers[n] || null; } }
                };
            };
        """.trimIndent()

        fun transformScript(source: String): String {
            val withoutImports = source.lines().filterNot { line ->
                val trimmed = line.trimStart()
                trimmed.startsWith("import ") || trimmed.startsWith("import\"") || trimmed.startsWith("import'") || trimmed.startsWith("export *")
            }.joinToString("\n")
            var transformed = withoutImports
            transformed = transformed.replace(Regex("""export\s+async\s+function"""), "function")
            transformed = transformed.replace(Regex("""export\s+function"""), "function")
            transformed = transformed.replace(Regex("""export\s+const\s+"""), "const ")
            transformed = transformed.replace(Regex("""export\s+let\s+"""), "let ")
            transformed = transformed.replace(Regex("""export\s+var\s+"""), "var ")
            transformed = transformed.replace(Regex("""export\s+default\s+"""), "")
            transformed = transformed.replace(Regex("""export\s*\{[^}]*\};?"""), "")
            transformed = transformed.replace(Regex("""\basync\s+"""), "")
            transformed = transformed.replace(Regex("""\bawait\s+"""), "")
            transformed = transformed.replace("Promise.all", "__tobiAll")
            return transformed
        }
    }
}
