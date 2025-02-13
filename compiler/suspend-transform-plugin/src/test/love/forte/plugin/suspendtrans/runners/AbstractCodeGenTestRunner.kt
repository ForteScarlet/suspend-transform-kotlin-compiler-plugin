package love.forte.plugin.suspendtrans.runners

import org.jetbrains.kotlin.test.backend.handlers.AsmLikeInstructionListingHandler
import org.jetbrains.kotlin.test.backend.handlers.BytecodeListingHandler
import org.jetbrains.kotlin.test.backend.handlers.IrTextDumpHandler
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.configureFirHandlersStep
import org.jetbrains.kotlin.test.builders.configureJvmArtifactsHandlersStep
import org.jetbrains.kotlin.test.builders.irHandlersStep
import org.jetbrains.kotlin.test.directives.AsmLikeInstructionListingDirectives.CHECK_ASM_LIKE_INSTRUCTIONS
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirCfgDumpHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirDiagnosticsHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirDumpHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirResolvedTypesVerifier

abstract class AbstractCodeGenTestRunner : AbstractTestRunner() {
    override fun TestConfigurationBuilder.configureHandlers() {
        configureFirHandlersStep {
            useHandlers(
                ::FirDumpHandler, ::FirCfgDumpHandler, ::FirResolvedTypesVerifier, ::FirDiagnosticsHandler,
            )
        }

        irHandlersStep {
            useHandlers(::IrTextDumpHandler)
        }

        configureJvmArtifactsHandlersStep {
            useHandlers(::BytecodeListingHandler, ::AsmLikeInstructionListingHandler)
        }

        defaultDirectives {
            +CHECK_ASM_LIKE_INSTRUCTIONS
        }
    }
}
