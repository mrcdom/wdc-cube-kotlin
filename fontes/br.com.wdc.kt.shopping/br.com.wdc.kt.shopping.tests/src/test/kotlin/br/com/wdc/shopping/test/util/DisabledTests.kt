package br.com.wdc.shopping.test.util

import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Desabilita, numa subclasse concreta, testes herdados de uma classe abstrata —
 * equivalente a `@Disabled("motivo")`, que não pode ser aplicado a um método herdado.
 *
 * @param reasons nome do método de teste → motivo
 */
class DisabledTests(private val reasons: Map<String, String>) : ExecutionCondition {

    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        val method = context.testMethod.orElse(null) ?: return ConditionEvaluationResult.enabled("not a test method")
        val reason = reasons[method.name] ?: return ConditionEvaluationResult.enabled("not listed")
        return ConditionEvaluationResult.disabled(reason)
    }
}
