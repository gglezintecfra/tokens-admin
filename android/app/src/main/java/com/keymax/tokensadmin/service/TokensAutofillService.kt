package com.keymax.tokensadmin.service

import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.keymax.tokensadmin.data.TipoAutenticacion
import com.keymax.tokensadmin.data.TokenRepository

class TokensAutofillService : AutofillService() {

    private val repository by lazy { TokenRepository(this) }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return
        val appPackageName = structure.activityComponent.packageName

        if (appPackageName == packageName) {
            callback.onSuccess(null)
            return
        }

        val tokens = repository.search()
        val userPassTokens = tokens.filter { it.tipo == TipoAutenticacion.USUARIO_CONTRASENA && it.usuario.isNotBlank() }

        if (userPassTokens.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val fillResponseBuilder = FillResponse.Builder()
        val parsedFields = parseStructure(structure)

        if (parsedFields.usernameId != null && parsedFields.passwordId != null) {
            for (token in userPassTokens) {
                val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                    setTextViewText(android.R.id.text1, "${token.aplicacion} (${token.usuario})")
                }

                val datasetBuilder = Dataset.Builder(presentation)
                datasetBuilder.setValue(parsedFields.usernameId, AutofillValue.forText(token.usuario))
                datasetBuilder.setValue(parsedFields.passwordId, AutofillValue.forText(token.contrasena))

                fillResponseBuilder.addDataset(datasetBuilder.build())
            }
        }

        callback.onSuccess(fillResponseBuilder.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private data class AutofillFields(
        val usernameId: android.view.autofill.AutofillId?,
        val passwordId: android.view.autofill.AutofillId?
    )

    private fun parseStructure(structure: AssistStructure): AutofillFields {
        var usernameId: android.view.autofill.AutofillId? = null
        var passwordId: android.view.autofill.AutofillId? = null

        val nodes = ArrayDeque<AssistStructure.ViewNode>()
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            windowNode.rootViewNode?.let { nodes.add(it) }
        }

        while (nodes.isNotEmpty()) {
            val node = nodes.removeFirst()
            val hints = node.autofillHints
            val id = node.autofillId

            if (id != null && hints != null) {
                for (hint in hints) {
                    val h = hint.lowercase()
                    if (h.contains("username") || h.contains("email") || h.contains("login")) {
                        if (usernameId == null) usernameId = id
                    } else if (h.contains("password")) {
                        if (passwordId == null) passwordId = id
                    }
                }
            }

            for (j in 0 until node.childCount) {
                node.getChildAt(j)?.let { nodes.add(it) }
            }
        }

        return AutofillFields(usernameId, passwordId)
    }
}
