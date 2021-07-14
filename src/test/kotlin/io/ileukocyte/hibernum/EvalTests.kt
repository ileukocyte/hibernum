package io.ileukocyte.hibernum

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.script.ScriptEngineManager
//import kotlin.script.experimental.jvmhost.jsr223.KotlinJsr223ScriptEngineImpl
import kotlin.system.measureTimeMillis

class EvalTests {
    @Test
    fun `test get all availiable script engines`() {
        for (factory in ScriptEngineManager().engineFactories) {
            with(buildString {
                appendLine(factory.engineName)
                appendLine(factory.engineVersion)
                appendLine(factory.extensions)
                appendLine(factory.languageName)
                appendLine(factory.languageVersion)
                appendLine("---------------------")
            }) {
                println(this)
            }
        }
    }

    @Test
    fun `test Kotlin evaluation without bindings`() {
        val engine = ScriptEngineManager().getEngineByExtension("kts")
        Assertions.assertNotNull(engine)
        val result = engine.eval(""""foo".length""")
        Assertions.assertEquals(result, 3)
    }

    @Test
    fun `test Kotlin evaluation with bindings`() {
        val engine = ScriptEngineManager().getEngineByExtension("kts")
        Assertions.assertNotNull(engine)
        engine.put("str", "teststring")
        val result = engine.eval("""str.removePrefix("test").length""")
        Assertions.assertEquals(result, 6)
    }

    @Test
    fun `test Kotlin evaluation time`() {
        fun <T> measureTimeMillisWithResult(block: () -> T) : Pair<Long, T> {
            val start = System.currentTimeMillis()
            val result = block()
            return Pair(System.currentTimeMillis() - start, result)
        }
        val (millisEngine, engine) = measureTimeMillisWithResult {
            ScriptEngineManager().getEngineByExtension("kts")!!// as KotlinJsr223ScriptEngineImpl
        }
        println("$millisEngine ms")
        engine.put("str", "class is")
        // first evaluation
        runBlocking {
            println(measureTimeMillis {
                val deferred = async { engine.eval("Unit") }
                deferred.await()
            })
        }
        repeat(10) {
            /*engine.state.history.let {
                if (it.isNotEmpty()) {
                    it.reset()
                }
            }*/
            val (millisResult, result) = measureTimeMillisWithResult {
                engine.eval("\"\$str \$this\"")
            }
            println(result)
            println("$millisResult ms")
        }
    }

    @Test
    fun `test retrieving bindings from within coroutines`() {
        val engine = ScriptEngineManager().getEngineByExtension("kts")
        Assertions.assertNotNull(engine)
       // Assertions.assertThrows(NoSuchMethodError::class.java) {
            engine.put("example", "test example string")
            println(engine.eval(
                """import kotlinx.coroutines.*
                    |
                    |fun test(block: suspend () -> Unit) = GlobalScope.launch { block() }
                    |test { println(example) }
                """.trimMargin()
            ))
       // }
    }
}