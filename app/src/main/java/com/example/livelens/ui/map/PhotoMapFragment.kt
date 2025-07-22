    package com.example.livelens.ui.map

    import android.app.AlertDialog
import android.graphics.BitmapFactory
    import android.net.Uri
    import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.livelens.R
import com.example.livelens.data.db.PhotoDao
import com.example.livelens.data.db.PhotoDatabase
import com.example.livelens.data.db.PhotoEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style

    class PhotoMapFragment : Fragment(R.layout.fragment_photo_map), OnMapReadyCallback {

        private lateinit var mapView: MapView
        private var mapLibreMap: MapLibreMap? = null

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            mapView = view.findViewById(R.id.photoMapView)
            mapView.onCreate(savedInstanceState)
            mapView.getMapAsync(this)
        }

        override fun onMapReady(map: MapLibreMap) {
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")) {
                lifecycleScope.launch {
                    val photoList: List<PhotoEntry> = withContext(Dispatchers.IO) {
                        photoDao.getAll()
                    }



                    for (photo in photoList) {
                        val markerOptions = MarkerOptions()
                            .position(LatLng(photo.latitude, photo.longitude))
                            .title("Photo taken on ${photo.timestamp}")
                            .snippet(photo.filePath)

                        map.addMarker(markerOptions)
                    }

                    map.setOnMarkerClickListener { marker ->
                        val path = marker.snippet ?: return@setOnMarkerClickListener false
                        showPhotoPopup(path)
                        true
                    }
                }

            }
        }
        private fun showPhotoPopup(filePath: String) {
            val dialog = AlertDialog.Builder(requireContext()).create()
            val imageView = ImageView(requireContext())
            imageView.setPadding(20, 20, 20, 20)
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP

            try {
                val uri = Uri.parse(filePath)
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)

                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap) // ✅ Load actual photo
                } else {
                    imageView.setImageResource(android.R.drawable.ic_menu_report_image) // fallback if decoding fails
                }
            } catch (e: Exception) {
                e.printStackTrace()
                imageView.setImageResource(R.drawable.ic_launcher_background)
            }

            dialog.setView(imageView)
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Close") { d, _ -> d.dismiss() }
            dialog.show()
        }




        private fun addDummyMarkers(map: MapLibreMap) {
            // Add a few dummy photo locations
            val photoPoints = listOf(
                LatLng(28.6139, 77.2090), // Delhi
                LatLng(19.0760, 72.8777), // Mumbai
                LatLng(12.9716, 77.5946)  // Bangalore
            )

            for ((index, location) in photoPoints.withIndex()) {
                map.addMarker(
                    MarkerOptions()
                        .position(location)
                        .title("Photo #${index + 1}")
                        .snippet("Captured at: ${location.latitude}, ${location.longitude}")
                )
            }
        }

        private lateinit var photoDao: PhotoDao

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            photoDao = PhotoDatabase.getInstance(requireContext()).photoDao()
        }


        override fun onResume() {
            super.onResume()
            mapView.onResume()
        }

        override fun onPause() {
            super.onPause()
            mapView.onPause()
        }

        override fun onStop() {
            super.onStop()
            mapView.onStop()
        }

        override fun onLowMemory() {
            super.onLowMemory()
            mapView.onLowMemory()
        }

        override fun onDestroyView() {
            super.onDestroyView()
            mapView.onDestroy()
        }
    }