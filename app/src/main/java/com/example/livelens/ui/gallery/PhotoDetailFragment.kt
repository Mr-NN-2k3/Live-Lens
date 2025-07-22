package com.example.livelens.ui.gallery

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.livelens.data.db.PhotoDatabase
import com.example.livelens.data.db.PhotoEntry
import com.example.livelens.databinding.FragmentPhotoDetailBinding
import kotlinx.coroutines.launch
import java.io.File

class PhotoDetailFragment : Fragment() {

    private var _binding: FragmentPhotoDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var photoEntry: PhotoEntry

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhotoDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Step 1: Get arguments (assume passed via navArgs or bundle)
        arguments?.let {
            photoEntry = it.getParcelable("photoEntry")!!
            bindPhotoData(photoEntry)
        }

        // Step 2: Handle buttons
        binding.shareButton.setOnClickListener { sharePhoto(photoEntry) }
        binding.deleteButton.setOnClickListener { confirmDelete(photoEntry) }
        binding.pinButton.setOnClickListener { setAsPin(photoEntry) }
    }

    private fun bindPhotoData(entry: PhotoEntry) {
        // Load image
        Glide.with(requireContext())
            .load(Uri.parse(entry.uri))
            .into(binding.fullImageView)

        // Bind metadata
        binding.photoTimestamp.text = "Captured on: ${entry.timestamp}"
        binding.photoLocation.text =
            "Location: ${entry.address ?: "${entry.latitude}, ${entry.longitude}"}"
        binding.photoWeather.text = "Weather: ${entry.weather ?: "Unknown"}"
    }

    private fun sharePhoto(entry: PhotoEntry) {
        val uri = Uri.parse(entry.uri)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            Intent.setType = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "📍 Location: ${entry.address ?: "${entry.latitude}, ${entry.longitude}"}\n" +
                        "📸 Captured on: ${entry.timestamp}\n" +
                        "☁️ Weather: ${entry.weather ?: "Unknown"}"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Photo"))
    }

    private fun confirmDelete(entry: PhotoEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Photo")
            .setMessage("Are you sure you want to delete this photo?")
            .setPositiveButton("Delete") { _, _ ->
                deletePhoto(entry)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePhoto(entry: PhotoEntry) {
        lifecycleScope.launch {
            val db = PhotoDatabase.Companion.getInstance(requireContext())
            db.photoDao().delete(entry)

            // Also delete the actual file
            val file = File(Uri.parse(entry.uri).path!!)
            if (file.exists()) file.delete()

            Toast.makeText(requireContext(), "Photo deleted", Toast.LENGTH_SHORT).show()
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setAsPin(entry: PhotoEntry) {
        lifecycleScope.launch {
            val db = PhotoDatabase.Companion.getInstance(requireContext())
            db.photoDao().setPinned(entry.id, true)
            Toast.makeText(requireContext(), "Pinned as memory!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}