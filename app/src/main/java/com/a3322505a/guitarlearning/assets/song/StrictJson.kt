package com.a3322505a.guitarlearning.assets.song

import com.a3322505a.guitarlearning.music.score.*
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** No duplicate keys, non-finite numbers, replacement UTF-8, comments or lenient syntax. */
object StrictJson {
    fun encode(value: Any?): ByteArray {
        fun text(value: String): String = buildString {
            append('"')
            value.forEach { c -> append(when(c) {
                '"' -> "\\\"";'\\' -> "\\\\";'\n' -> "\\n";'\r' -> "\\r";'\t' -> "\\t"
                else -> if(c<' ') "\\u"+c.code.toString(16).padStart(4,'0') else c.toString()
            }) }
            append('"')
        }
        fun json(v: Any?): String = when(v) {
            null -> "null"
            is String -> text(v)
            is Boolean,is Int,is Long,is BigDecimal -> v.toString()
            is Double -> { require(v.isFinite());v.toString() }
            is List<*> -> v.joinToString(",","[","]") { json(it) }
            is Map<*,*> -> v.entries.joinToString(",","{","}") { text(it.key as String)+":"+json(it.value) }
            else -> error("Unsupported internal JSON value")
        }
        return json(value).toByteArray()
    }
    fun parse(bytes: ByteArray): Any? {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return Parser(decoder.decode(ByteBuffer.wrap(bytes)).toString()).parse()
    }
    private class Parser(val input: String) {
        var at = 0
        var nodes = 0
        fun error(reason: String): Nothing = throw MusicError(Diagnostic("JSON","/byte/"+at,reason))
        fun ws() { while(at<input.length && input[at] in " \t\r\n") at++ }
        fun take(c: Char) { ws(); if(at>=input.length || input[at++]!=c) error("Expected "+c) }
        fun parse(): Any? { val value=value(0); ws(); if(at!=input.length) error("Trailing data"); return value }
        fun value(depth: Int): Any? {
            if(depth>48 || ++nodes>2_000_000) error("JSON resource limit")
            ws(); if(at==input.length) error("Unexpected end")
            return when(input[at]) {
                '{' -> {
                    at++; ws(); val result=linkedMapOf<String,Any?>()
                    if(at<input.length && input[at]=='}') { at++; result } else {
                        while(true) {
                            ws(); if(at>=input.length || input[at]!='"') error("Expected object key")
                            val key=string(); if(result.containsKey(key)) error("Duplicate key: "+key)
                            take(':'); result[key]=value(depth+1); ws()
                            if(at<input.length && input[at]=='}') { at++; break }; take(',')
                        }; result
                    }
                }
                '[' -> {
                    at++; ws(); val result=mutableListOf<Any?>()
                    if(at<input.length && input[at]==']') { at++; result } else {
                        while(true) {
                            result+=value(depth+1); ws()
                            if(at<input.length && input[at]==']') { at++; break }; take(',')
                        }; result
                    }
                }
                '"' -> string()
                't' -> literal("true",true)
                'f' -> literal("false",false)
                'n' -> literal("null",null)
                else -> {
                    val start=at
                    while(at<input.length && input[at] in "-+0123456789.eE") at++
                    val token=input.substring(start,at)
                    if(token.length>64 || !token.matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))) error("Invalid number")
                    val number=token.toBigDecimalOrNull() ?: error("Invalid number")
                    if(number.scale().let { kotlin.math.abs(it) }>1000 || !number.toDouble().isFinite()) error("Number limit")
                    number
                }
            }
        }
        fun literal(token: String, result: Any?): Any? {
            if(!input.startsWith(token,at)) error("Invalid literal")
            at+=token.length; return result
        }
        fun string(): String {
            take('"'); val result=StringBuilder()
            while(at<input.length) {
                val c=input[at++]
                if(c=='"') {
                    val text=result.toString()
                    var i=0
                    while(i<text.length) {
                        if(Character.isHighSurrogate(text[i])) {
                            if(i+1>=text.length || !Character.isLowSurrogate(text[i+1])) error("Unpaired surrogate")
                            i+=2
                        } else { if(Character.isLowSurrogate(text[i])) error("Unpaired surrogate"); i++ }
                    }
                    return text
                }
                if(c<' ') error("Control character")
                if(c=='\\') {
                    if(at>=input.length) error("Escape end")
                    result.append(when(val e=input[at++]) {
                        '"','\\','/' -> e
                        'b' -> '\b'; 'f' -> '\u000c'; 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'
                        'u' -> {
                            if(at+4>input.length) error("Unicode escape end")
                            val hex=input.substring(at,at+4); at+=4
                            (hex.toIntOrNull(16) ?: error("Invalid Unicode escape")).toChar()
                        }
                        else -> error("Invalid escape")
                    })
                } else result.append(c)
                if(result.length>262144) error("String resource limit")
            }
            error("Unterminated string")
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.objectValue() = this as Map<String,Any?>
internal fun Any?.arrayValue() = this as List<Any?>
internal fun Any?.textValue() = this as String
internal fun Any?.longValue() = (this as BigDecimal).longValueExact()
internal fun Any?.intValue() = (this as BigDecimal).intValueExact()
internal fun Any?.doubleValue() = (this as BigDecimal).toDouble()
internal fun Any?.qValue(): Q = objectValue().let { Q(it["n"].longValue(),it["d"].longValue()) }

/** The App consumes the identical closed schemas shipped to the producer. */
class SongSchema(load: (String)->ByteArray) {
    private val schemas by lazy { listOf("manifest","score","practices","provenance","review")
        .associateWith { StrictJson.parse(load(it+".schema.json")).objectValue() } }
    fun validate(name: String, data: Any?) = visit(schemas.getValue(name),data,"/"+name,schemas.getValue(name))
    private fun visit(schema: Map<String,Any?>, data: Any?, path: String, root: Map<String,Any?>) {
        fun check(ok: Boolean, message: String) = musicCheck(ok,"SCHEMA",path,message)
        val ref=schema["\$ref"] as? String
        if(ref!=null) {
            visit(root["\$defs"].objectValue()[ref.substringAfterLast('/')].objectValue(),data,path,root)
            if(ref=="#/\$defs/q") {
                val value=data.objectValue();val q=value.qValue()
                musicCheck(q.n==value["n"].longValue() && q.d==value["d"].longValue(),"Q",path,"Rational must be canonical")
            }
            return
        }
        val options=schema["enum"] as? List<*>
        if(options!=null) check(options.any { equalJson(it,data) },"Value outside enum")
        when(schema["type"]) {
            "object" -> {
                check(data is Map<*,*>,"Expected object"); val map=data.objectValue()
                val properties=(schema["properties"] as? Map<*,*>)?.let { schema["properties"].objectValue() }.orEmpty()
                val patterns=(schema["patternProperties"] as? Map<*,*>)?.let { schema["patternProperties"].objectValue() }.orEmpty()
                (schema["required"] as? List<*>)?.forEach { check(map.containsKey(it),"Missing field "+it) }
                (schema["maxProperties"] as? BigDecimal)?.let { check(map.size<=it.toInt(),"Too many properties") }
                map.forEach { (key,value) ->
                    val rule=properties[key] ?: patterns.entries.firstOrNull { Regex(it.key).containsMatchIn(key) }?.value
                    val pointer=path+"/"+key.replace("~","~0").replace("/","~1")
                    if(rule==null) musicCheck(schema["additionalProperties"]!=false,"SCHEMA",pointer,"Unknown field")
                    else visit(rule.objectValue(),value,pointer,root)
                }
            }
            "array" -> {
                check(data is List<*>,"Expected array"); val list=data.arrayValue()
                (schema["minItems"] as? BigDecimal)?.let { check(list.size>=it.toInt(),"Too few items") }
                (schema["maxItems"] as? BigDecimal)?.let { check(list.size<=it.toInt(),"Too many items") }
                list.forEachIndexed { i,value -> visit(schema["items"].objectValue(),value,path+"/"+i,root) }
            }
            "string" -> {
                check(data is String,"Expected string"); val text=data.textValue()
                (schema["maxLength"] as? BigDecimal)?.let { check(text.codePointCount(0,text.length)<=it.toInt(),"Text too long") }
                (schema["pattern"] as? String)?.let { check(Regex(it).containsMatchIn(text),"Invalid identifier") }
            }
            "number","integer" -> {
                check(data is BigDecimal,"Expected number"); val number=data as BigDecimal
                if(schema["type"]=="integer") check(number.stripTrailingZeros().scale()<=0,"Expected integer")
                (schema["minimum"] as? BigDecimal)?.let { check(number>=it,"Number below limit") }
                (schema["maximum"] as? BigDecimal)?.let { check(number<=it,"Number above limit") }
            }
            "boolean" -> check(data is Boolean,"Expected boolean")
        }
    }
    private fun equalJson(a: Any?, b: Any?) = if(a is BigDecimal && b is BigDecimal) a.compareTo(b)==0 else a==b
}
