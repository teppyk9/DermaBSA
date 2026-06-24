package com.uninsubria.derma_bsa.ui.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.uninsubria.derma_bsa.AppViewModel
import com.uninsubria.derma_bsa.R
import com.uninsubria.derma_bsa.databinding.FragmentCameraBinding
import com.uninsubria.derma_bsa.util.OnnxHelper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Secondo step del flusso di misurazione: acquisizione dell'immagine.
 *
 * L'utente può scattare una foto con la fotocamera posteriore oppure
 * selezionarne una dalla galleria. In entrambi i casi l'immagine viene
 * salvata nel ViewModel e si naviga a [CropFragment] per il ritaglio.
 */
class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by activityViewModels()
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else Toast.makeText(requireContext(), "Permesso camera negato", Toast.LENGTH_SHORT).show()
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) navigateToCrop(bitmap)
            else Toast.makeText(requireContext(), "Impossibile caricare immagine", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Errore galleria: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        OnnxHelper.init(requireContext())
        cameraExecutor = Executors.newSingleThreadExecutor()

        val region = viewModel.selectedRegion.value
        binding.tvDistretto.text = "Distretto: ${region?.label ?: "-"} (${region?.bsaPercent?.toInt() ?: "-"}%)"

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnCapture.setOnClickListener { takePhoto() }
        binding.btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }
    }

    /**
     * Avvia il binding della fotocamera posteriore alla PreviewView
     * e prepara l'oggetto [ImageCapture] per la cattura.
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Errore camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /**
     * Scatta una foto con la fotocamera e, in caso di successo, avvia il flusso
     * di ritaglio navigando a [CropFragment].
     */
    private fun takePhoto() {
        val capture = imageCapture ?: return
        binding.btnCapture.isEnabled = false
        capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = image.toBitmap()
                image.close()
                requireActivity().runOnUiThread {
                    binding.btnCapture.isEnabled = true
                    navigateToCrop(bitmap)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                requireActivity().runOnUiThread {
                    binding.btnCapture.isEnabled = true
                    Toast.makeText(requireContext(), "Errore: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    /**
     * Salva il bitmap nel ViewModel e naviga a [CropFragment].
     *
     * @param bitmap immagine appena scattata o selezionata dalla galleria
     */
    private fun navigateToCrop(bitmap: Bitmap) {
        viewModel.setCroppedBitmap(bitmap)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, CropFragment())
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}
