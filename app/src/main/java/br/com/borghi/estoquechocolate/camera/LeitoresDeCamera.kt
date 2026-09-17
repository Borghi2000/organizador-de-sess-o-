package br.com.borghi.estoquechocolate.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * As duas cameras do app.
 *
 * Codigo de barras roda continuo, porque e leitura de um numero e acerta ou nao acerta na hora.
 * Etiqueta e uma foto so, com lanterna e toque para focar: OCR continuo no video gasta bateria,
 * fica tremendo e le pior que uma foto parada.
 */

@Composable
private fun PreviaDaCamera(
    modifier: Modifier = Modifier,
    aoLigar: (ProcessCameraProvider, PreviewView, androidx.lifecycle.LifecycleOwner) -> Camera?,
    aoMudarCamera: (Camera?) -> Unit,
) {
    val contexto = LocalContext.current
    val donoDoCiclo = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previa = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val futuro = ProcessCameraProvider.getInstance(ctx)
            futuro.addListener(
                {
                    val provedor = runCatching { futuro.get() }.getOrNull() ?: return@addListener
                    val camera = runCatching { aoLigar(provedor, previa, donoDoCiclo) }.getOrNull()
                    aoMudarCamera(camera)
                    camera?.let { ligarToqueParaFocar(previa, it) }
                },
                ContextCompat.getMainExecutor(ctx),
            )
            previa
        },
    )
}

private fun ligarToqueParaFocar(previa: PreviewView, camera: Camera) {
    previa.setOnTouchListener { visao, evento ->
        if (evento.action == android.view.MotionEvent.ACTION_UP) {
            val ponto = previa.meteringPointFactory.createPoint(evento.x, evento.y)
            val acao = androidx.camera.core.FocusMeteringAction.Builder(ponto).build()
            runCatching { camera.cameraControl.startFocusAndMetering(acao) }
            visao.performClick()
        }
        true
    }
}

@Composable
private fun BarraDeTopo(titulo: String, instrucao: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC000000), contentColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(instrucao, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Le codigo de barras continuamente e devolve o primeiro que reconhecer. */
@Composable
fun LeitorDeCodigoDeBarras(
    titulo: String,
    instrucao: String,
    aoLer: (String) -> Unit,
    aoDigitarNaMao: () -> Unit,
    aoVoltar: () -> Unit,
) {
    val contexto = LocalContext.current
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val leitor: BarcodeScanner = remember { BarcodeScanning.getClient() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var lanterna by remember { mutableStateOf(false) }
    var jaLeu by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { leitor.close() }
            executor.shutdown()
        }
    }

    ComPermissaoDeCamera(aoDigitarNaMao = aoDigitarNaMao) {
        Box(modifier = Modifier.fillMaxSize()) {
            PreviaDaCamera(
                modifier = Modifier.fillMaxSize(),
                aoLigar = { provedor, previa, dono ->
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previa.surfaceProvider)
                    }
                    val analise = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analise.setAnalyzer(executor) { imagem ->
                        analisarCodigo(imagem, leitor) { codigo ->
                            if (!jaLeu) {
                                jaLeu = true
                                aoLer(codigo)
                            }
                        }
                    }
                    provedor.unbindAll()
                    provedor.bindToLifecycle(dono, CameraSelector.DEFAULT_BACK_CAMERA, preview, analise)
                },
                aoMudarCamera = { camera = it },
            )

            Column(modifier = Modifier.align(Alignment.TopCenter)) {
                BarraDeTopo(titulo, instrucao)
            }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        lanterna = !lanterna
                        runCatching { camera?.cameraControl?.enableTorch(lanterna) }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (lanterna) "Apagar luz" else "Lanterna") }
                Button(onClick = aoDigitarNaMao, modifier = Modifier.weight(1f)) { Text("Digitar na mao") }
                OutlinedButton(onClick = aoVoltar, modifier = Modifier.weight(1f)) { Text("Voltar") }
            }
        }
    }
}

private fun analisarCodigo(imagem: ImageProxy, leitor: BarcodeScanner, aoAchar: (String) -> Unit) {
    val bitmap = runCatching { imagem.toBitmap() }.getOrNull()
    if (bitmap == null) {
        imagem.close()
        return
    }
    val entrada = InputImage.fromBitmap(bitmap, imagem.imageInfo.rotationDegrees)
    leitor.process(entrada)
        .addOnSuccessListener { codigos ->
            codigos.firstNotNullOfOrNull { it.rawValue }?.let(aoAchar)
        }
        .addOnCompleteListener { imagem.close() }
}

/** O que a foto da etiqueta devolveu: o texto lido pelo OCR e o arquivo da foto guardada. */
data class FotoDaEtiqueta(val texto: String, val arquivo: String)

/**
 * Tira uma foto da etiqueta e devolve o texto reconhecido no aparelho. A foto fica guardada para
 * poder ser conferida depois, numa divergencia.
 */
@Composable
fun LeitorDeEtiqueta(
    aoLer: (FotoDaEtiqueta) -> Unit,
    aoDigitarNaMao: () -> Unit,
    aoVoltar: () -> Unit,
) {
    val contexto = LocalContext.current
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val ocr = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val captura = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var lanterna by remember { mutableStateOf(false) }
    var lendo by remember { mutableStateOf(false) }
    var falha by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ocr.close() }
            executor.shutdown()
        }
    }

    ComPermissaoDeCamera(aoDigitarNaMao = aoDigitarNaMao) {
        Box(modifier = Modifier.fillMaxSize()) {
            PreviaDaCamera(
                modifier = Modifier.fillMaxSize(),
                aoLigar = { provedor, previa, dono ->
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previa.surfaceProvider)
                    }
                    provedor.unbindAll()
                    provedor.bindToLifecycle(dono, CameraSelector.DEFAULT_BACK_CAMERA, preview, captura)
                },
                aoMudarCamera = { camera = it },
            )

            Column(modifier = Modifier.align(Alignment.TopCenter)) {
                BarraDeTopo(
                    "Ler etiqueta",
                    "Enquadre a validade e o lote. Toque na tela para focar. Uma foto parada le " +
                        "melhor que video.",
                )
                falha?.let {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) { Text(it, modifier = Modifier.padding(12.dp)) }
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (lendo) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                Button(
                    onClick = {
                        if (lendo) return@Button
                        lendo = true
                        falha = null
                        fotografarELer(
                            contexto = contexto,
                            captura = captura,
                            executor = executor,
                            ocr = ocr,
                            aoTerminar = { resultado ->
                                lendo = false
                                resultado.onSuccess(aoLer).onFailure {
                                    falha = "Nao consegui ler: ${it.message ?: "tente de novo com mais luz"}"
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (lendo) "Lendo..." else "Fotografar etiqueta") }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            lanterna = !lanterna
                            runCatching { camera?.cameraControl?.enableTorch(lanterna) }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (lanterna) "Apagar luz" else "Lanterna") }
                    OutlinedButton(onClick = aoDigitarNaMao, modifier = Modifier.weight(1f)) {
                        Text("Digitar na mao")
                    }
                    OutlinedButton(onClick = aoVoltar, modifier = Modifier.weight(1f)) { Text("Voltar") }
                }
            }
        }
    }
}

private fun fotografarELer(
    contexto: Context,
    captura: ImageCapture,
    executor: ExecutorService,
    ocr: com.google.mlkit.vision.text.TextRecognizer,
    aoTerminar: (Result<FotoDaEtiqueta>) -> Unit,
) {
    val nome = Fotos.novoNome("etiqueta")
    val arquivo = File(Fotos.pasta(contexto), nome)
    val saida = ImageCapture.OutputFileOptions.Builder(arquivo).build()
    val principal = ContextCompat.getMainExecutor(contexto)

    captura.takePicture(
        saida,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(resultado: ImageCapture.OutputFileResults) {
                val entrada = runCatching { InputImage.fromFilePath(contexto, Uri.fromFile(arquivo)) }
                    .getOrElse { erro ->
                        principal.execute { aoTerminar(Result.failure(erro)) }
                        return
                    }
                ocr.process(entrada)
                    .addOnSuccessListener { texto ->
                        principal.execute { aoTerminar(Result.success(FotoDaEtiqueta(texto.text, nome))) }
                    }
                    .addOnFailureListener { erro ->
                        principal.execute { aoTerminar(Result.failure(erro)) }
                    }
            }

            override fun onError(excecao: ImageCaptureException) {
                principal.execute { aoTerminar(Result.failure(excecao)) }
            }
        },
    )
}

/**
 * Foto simples, sem reconhecimento: usada no cadastro do produto, para bater o olho e reconhecer
 * sem precisar ler o nome.
 */
@Composable
fun CapturaDeFoto(
    titulo: String,
    aoTirar: (String) -> Unit,
    aoVoltar: () -> Unit,
) {
    val contexto = LocalContext.current
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val captura = remember { ImageCapture.Builder().build() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var falha by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    ComPermissaoDeCamera(aoDigitarNaMao = aoVoltar) {
        Box(modifier = Modifier.fillMaxSize()) {
            PreviaDaCamera(
                modifier = Modifier.fillMaxSize(),
                aoLigar = { provedor, previa, dono ->
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previa.surfaceProvider)
                    }
                    provedor.unbindAll()
                    provedor.bindToLifecycle(dono, CameraSelector.DEFAULT_BACK_CAMERA, preview, captura)
                },
                aoMudarCamera = { camera = it },
            )

            Column(modifier = Modifier.align(Alignment.TopCenter)) {
                BarraDeTopo(titulo, "Toque na tela para focar.")
                falha?.let { Text(it, color = Color.White, modifier = Modifier.padding(12.dp)) }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val nome = Fotos.novoNome("produto")
                        val arquivo = File(Fotos.pasta(contexto), nome)
                        val saida = ImageCapture.OutputFileOptions.Builder(arquivo).build()
                        val principal = ContextCompat.getMainExecutor(contexto)
                        captura.takePicture(
                            saida,
                            executor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(resultado: ImageCapture.OutputFileResults) {
                                    principal.execute { aoTirar(nome) }
                                }

                                override fun onError(excecao: ImageCaptureException) {
                                    principal.execute { falha = "Nao consegui tirar a foto." }
                                }
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Tirar foto") }
                OutlinedButton(onClick = aoVoltar, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
            }
        }
    }
}
