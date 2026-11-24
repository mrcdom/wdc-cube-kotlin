package br.com.wdc.store.test.util

import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Disables, in a concrete subclass, tests inherited from an abstract class —
 * equivalent to `@Disabled("reason")`, which cannot be applied to an inherited method.
 *
 * @param reasons test method name → reason
 */
class DisabledTests(private val reasons: Map<String, String>) : ExecutionCondition {

    override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult {
        val method = context.testMethod.orElse(null) ?: return ConditionEvaluationResult.enabled("not a test method")
        val reason = reasons[method.name] ?: return ConditionEvaluationResult.enabled("not listed")
        return ConditionEvaluationResult.disabled(reason)
    }
}
