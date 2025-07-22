    package com.example.livelens.ui.camera

    import android.Manifest
    import android.app.AlertDialog
    import android.content.ContentValues
    import android.content.Intent
    import android.content.pm.PackageManager
    import android.graphics.Bitmap
    import android.graphics.BitmapFactory
    import android.graphics.Canvas
    import android.graphics.Color
    import android.graphics.Matrix
    import android.graphics.Paint
    import android.graphics.Typeface
    import android.graphics.drawable.BitmapDrawable
    import android.location.Geocoder
    import android.location.Location
    import android.net.Uri
    import android.os.Build
    import android.os.Bundle
    import android.os.Looper
    import android.provider.MediaStore
    import android.provider.Settings
    import android.text.Layout
    import android.text.StaticLayout
    import android.text.TextPaint
    import android.util.Log
    import android.view.View
    import android.widget.Toast
    import androidx.activity.result.contract.ActivityResultContracts
    import androidx.annotation.RequiresPermission
    import androidx.camera.core.AspectRatio
    import androidx.camera.core.Camera
    import androidx.camera.core.CameraSelector
    import androidx.camera.core.ImageCapture
    import androidx.camera.core.ImageCaptureException
    import androidx.camera.core.Preview
    import androidx.camera.lifecycle.ProcessCameraProvider
    import androidx.core.app.ActivityCompat
    import androidx.core.content.ContextCompat
    import androidx.core.view.WindowCompat
    import androidx.core.view.WindowInsetsCompat
    import androidx.core.view.WindowInsetsControllerCompat
    import androidx.exifinterface.media.ExifInterface
    import androidx.fragment.app.Fragment
    import androidx.lifecycle.lifecycleScope
    import androidx.navigation.fragment.findNavController
    import coil.load
    import com.example.livelens.BuildConfig
    import com.example.livelens.R
    import com.example.livelens.data.db.PhotoDao
    import com.example.livelens.data.db.PhotoDatabase
    import com.example.livelens.data.db.PhotoEntry
    import com.example.livelens.data.weather.WeatherResponse
    import com.example.livelens.databinding.FragmentCameraBinding
    import com.google.android.gms.location.FusedLocationProviderClient
    import com.google.android.gms.location.LocationCallback
    import com.google.android.gms.location.LocationRequest
    import com.google.android.gms.location.LocationResult
    import com.google.android.gms.location.LocationServices
    import com.google.android.gms.location.Priority
    import com.squareup.moshi.Moshi
    import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
    import kotlinx.coroutines.CoroutineScope
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.Job
    import kotlinx.coroutines.delay
    import kotlinx.coroutines.isActive
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext
    import okhttp3.OkHttpClient
    import okhttp3.Request
    import java.time.LocalDateTime
    import java.time.format.DateTimeFormatter
    import java.util.Locale
    import kotlin.math.roundToInt

    class CameraFragment : Fragment(R.layout.fragment_camera) {

        private lateinit var binding: FragmentCameraBinding
        private lateinit var photoDao: PhotoDao
        private var currentLat: Double = 0.0
        private var currentLon: Double = 0.0
        enum class FlashMode {
            AUTO, ON, OFF
        }
        private var currentFlashMode = FlashMode.OFF
        private var imageCapture: ImageCapture? = null
        private var camera: Camera? = null
        private var currentLens = CameraSelector.LENS_FACING_BACK
        private var torchOn = false

        private lateinit var fusedClient: FusedLocationProviderClient
        private val locationRequest = LocationRequest.Builder(5_000L)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateDistanceMeters(2f)
            .build()
        private var locationCallback: LocationCallback? = null

        private val WEATHER_API_KEY = BuildConfig.WEATHER_API_KEY
        private var lastWeatherFetchTime = 0L
        private var lastWeatherLat = 0.0
        private var lastWeatherLon = 0.0
        private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        private val weatherJsonAdapter = moshi.adapter(WeatherResponse::class.java)
        private val okHttp = OkHttpClient()


        private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss")
        private val scope = CoroutineScope(Job() + Dispatchers.Main)

        private val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) @RequiresPermission(
                allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]
            ) {
                if (allPermissionsGranted()) {
                    startCamera()
                    startClock()
                    if (ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Toast.makeText(
                            requireContext(),
                            "Location permissions are required.",
                            Toast.LENGTH_SHORT
                        ).show()
                        // If permissions are not granted, we cannot start location updates
                        binding.txtCoords.text = "Location permissions not granted"
                        binding.txtAddress.text = "Location permissions not granted"
                        binding.txtTemp.text = "Location permissions not granted"
                        return@registerForActivityResult
                    }
                    startLocationUpdates()
                } else {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Permissions Required")
                        .setMessage("LiveLens needs camera and location to work.")
                        .setPositiveButton("Open Settings") { _, _ ->
                            startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", requireContext().packageName, null)
                                )
                            )
                        }
                        .setNegativeButton("Exit") { _, _ -> requireActivity().finish() }
                        .setCancelable(false)
                        .show()
                }
            }

        override fun onResume() {
            super.onResume()
            // Restart camera and location updates when fragment resumes
            startCamera()
            startClock()
            checkLocationPermissionAndStartUpdates()

            // Ensure location updates are started
            if (locationCallback == null){
                startLocationUpdates()

                //also call fetchWeather to update UI with current locationfetchWeather(currentLat, currentLon)
            } else {
                fusedClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
            }
            // Fetch current location and update UI
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(
                    requireContext(),
                    "Location permissions are required.",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            fusedClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    currentLat = location.latitude
                    currentLon = location.longitude
                    updateLocationUI(currentLat, currentLon)
                } else {
                    Log.w("CameraFragment", "No last known location available")
                    Toast.makeText(
                        requireContext(),
                        "waiting for location updates...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            updateLocationUI(currentLat, currentLon)
        }

        private fun checkLocationPermissionAndStartUpdates() {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                fusedClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback!!,
                    Looper.getMainLooper()
                )
            } else {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    1001 // Request code for location permission

                )
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            binding = FragmentCameraBinding.bind(view)

            binding.capture.setOnClickListener { onCaptureClick() }
            binding.switchCamera.setOnClickListener { onSwitchCameraClick() }
            binding.flash.setOnClickListener { onFlashClick() }
            binding.photoMap.setOnClickListener {
                findNavController().navigate(R.id.photoMapFragment)
            }

            fusedClient = LocationServices.getFusedLocationProviderClient(requireContext())

            if (allPermissionsGranted()) {
                startCamera()
                startClock()
                startLocationUpdates()
            } else {
                permissionLauncher.launch(REQUIRED_PERMISSIONS)
            }
        }


        private fun startClock() = scope.launch {
            while (isActive) {
                binding.txtTime.text = LocalDateTime.now().format(timeFormatter)
                delay(1_000)
            }
        }

        @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
        private fun startLocationUpdates() {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            if (locationCallback != null) return

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(res: LocationResult) {
                    val loc = res.lastLocation ?: return
                    currentLat = loc.latitude
                    currentLon = loc.longitude
                    binding.txtCoords.text = "Lat: %.5f  Lng: %.5f".format(loc.latitude, loc.longitude)

                    Geocoder(requireContext(), Locale.getDefault())
                        .getFromLocation(loc.latitude, loc.longitude, 1)
                        ?.firstOrNull()
                        ?.let { binding.txtAddress.text = it.getAddressLine(0) }

                    fetchWeather(loc.latitude, loc.longitude)
                }
            }

            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        }


        private fun fetchWeather(lat: Double, lon: Double) {
            val distance = FloatArray(1)
            Location.distanceBetween(lat, lon, lastWeatherLat, lastWeatherLon, distance)
            if (System.currentTimeMillis() - lastWeatherFetchTime < 60 && distance[0] < 250) return

            lastWeatherFetchTime = System.currentTimeMillis()
            lastWeatherLat = lat
            lastWeatherLon = lon

            scope.launch(Dispatchers.IO) {
                val url =
                    "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&units=metric&appid=$WEATHER_API_KEY"
                val req = Request.Builder().url(url).build()
                okHttp.newCall(req).execute().use { rsp ->
                    if (!rsp.isSuccessful) return@use
                    rsp.body?.string()?.let { json ->
                        val weather = weatherJsonAdapter.fromJson(json) ?: return@let
                        withContext(Dispatchers.Main) {
                            binding.txtTemp.text = "${weather.main.temp.roundToInt()}°C"
                            val iconUrl =
                                "https://openweathermap.org/img/wn/${weather.weather[0].icon}@2x.png"
                            binding.imgWeather.load(iconUrl)
                        }
                    }
                }
            }
        }

        private fun startCamera() {
            val future = ProcessCameraProvider.getInstance(requireContext())
            future.addListener({
                val provider = future.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setTargetRotation(binding.previewView.display.rotation)
                    .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(currentLens)
                    .build()

                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageCapture
                )
            }, ContextCompat.getMainExecutor(requireContext()))
        }

        private fun onCaptureClick() {
            val capture = imageCapture ?: return
            val name = "LiveLens_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.TITLE, name)
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LiveLens")
                }
            }

            val output = ImageCapture.OutputFileOptions.Builder(
                requireContext().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()

            capture.takePicture(
                output,
                ContextCompat.getMainExecutor(requireContext()),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Toast.makeText(
                            requireContext(),
                            "Capture failed: ${exc.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    override fun onImageSaved(res: ImageCapture.OutputFileResults) {
                        val uri = res.savedUri ?: return
                        stampImage(uri) // ✅ Uri is passed here

                        val photoFilePath = uri.toString() // ✅ Correct: saves full "content://" URI
                        lifecycleScope.launch {
                            val entry = PhotoEntry(
                                filePath = photoFilePath,
                                latitude = currentLat,
                                longitude = currentLon,
                                timestamp = System.currentTimeMillis()
                            )
                            photoDao.insert(entry)
                        }

                        Toast.makeText(requireContext(), "Saved with stamp!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        private fun stampImage(uri: Uri) {

            val exif = requireContext().contentResolver.openInputStream(uri)?.use {
                ExifInterface(it)
            } ?: return

            val bitmap0 = BitmapFactory.decodeStream(
                requireContext().contentResolver.openInputStream(uri)
            ) ?: return

            val rotation = when (exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            val rotatedBmp = if (rotation != 0) {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(bitmap0, 0, 0, bitmap0.width, bitmap0.height, m, true)
            } else bitmap0.copy(Bitmap.Config.ARGB_8888, true)

            val canvas = Canvas(rotatedBmp)
            val scaleFactor = rotatedBmp.width / 1080f // Use 1080p as baseline reference
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = 30f * scaleFactor
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(10f * scaleFactor, 2f, 2f, Color.BLACK)
            }
            val lines = listOf(
                "${binding.txtTime.text}               ${binding.txtTemp.text} ",
                binding.txtCoords.text.toString(),
                binding.txtAddress.text.toString(),
            )

            binding.imgWeather.drawable?.let { dr ->
                val hwBitmap = (dr as BitmapDrawable).bitmap
                val iconBmp = if (hwBitmap.config == Bitmap.Config.HARDWARE) {
                    hwBitmap.copy(Bitmap.Config.ARGB_8888, true)
                } else hwBitmap
                val iconSize = (48 * scaleFactor).roundToInt()
                val scaledIcon = Bitmap.createScaledBitmap(iconBmp, iconSize, iconSize, true)
                canvas.drawBitmap(
                    scaledIcon,
                    20f * scaleFactor,
                    rotatedBmp.height - iconSize - 20f * scaleFactor,
                    null
                )
            }

            val lineSpacing = paint.textSize + (10f * scaleFactor)
            val totalHeight = lines.size * lineSpacing
            var y = rotatedBmp.height - totalHeight - 20f * scaleFactor
            val backgroundPaint = Paint().apply {
                color = Color.argb(120, 0, 0, 0) // semi-transparent black
            }
            canvas.drawRect(
                10f * scaleFactor,
                rotatedBmp.height - totalHeight - 50f * scaleFactor,
                rotatedBmp.width - 10f * scaleFactor,
                rotatedBmp.height - 15f * scaleFactor,
                backgroundPaint
            )

            val textPaint = TextPaint(paint)
            val maxWidth = rotatedBmp.width - (40f * scaleFactor)
            for (line in lines) {
                val staticLayout = StaticLayout.Builder
                    .obtain(line, 0, line.length, textPaint, maxWidth.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build()

                canvas.save()
                canvas.translate(20f * scaleFactor, y)
                staticLayout.draw(canvas)
                canvas.restore()

                y += staticLayout.height
            }

            requireContext().contentResolver.openOutputStream(uri, "w")?.use { out ->
                rotatedBmp.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
        }
        private fun onFlashClick() {
            currentFlashMode = when (currentFlashMode) {
                FlashMode.OFF -> FlashMode.ON
                FlashMode.ON -> FlashMode.AUTO
                FlashMode.AUTO -> FlashMode.OFF
            }
            updateFlashMode()
        }
        private fun updateFlashMode() {
            when (currentFlashMode) {
                FlashMode.OFF -> {
                    imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
                    camera?.cameraControl?.enableTorch(false)
                    binding.flash.setImageResource(android.R.drawable.btn_star_big_off)
                }
                FlashMode.ON -> {
                    imageCapture?.flashMode = ImageCapture.FLASH_MODE_ON
                    camera?.cameraControl?.enableTorch(true)
                    binding.flash.setImageResource(android.R.drawable.btn_star_big_on)
                }
                FlashMode.AUTO -> {
                    imageCapture?.flashMode = ImageCapture.FLASH_MODE_AUTO
                    camera?.cameraControl?.enableTorch(false)
                    binding.flash.setImageResource(android.R.drawable.btn_radio)
                }
            }
        }



        private fun onSwitchCameraClick() {
            currentLens = if (currentLens == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            startCamera()
        }

        private fun allPermissionsGranted() =
            REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    it
                ) == PackageManager.PERMISSION_GRANTED
            }

        companion object {
            private val REQUIRED_PERMISSIONS = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val window = requireActivity().window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())

            photoDao = PhotoDatabase.getInstance(requireContext()).photoDao()
        }

        private fun updateLocationUI(lat: Double, lon: Double) {
            binding.txtCoords.text = "Lat: %.5f  Lng: %.5f".format(lat, lon)

            lifecycleScope.launch(Dispatchers.IO) {
                val address = Geocoder(requireContext(), Locale.getDefault())
                    .getFromLocation(lat, lon, 1)
                    ?.firstOrNull()
                    ?.getAddressLine(0)

                withContext(Dispatchers.Main) {
                    binding.txtAddress.text = address ?: "Unknown address"
                }
            }
        }
    }