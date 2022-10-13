package com.example.korailreservationapp.service.ui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.ImageView
import androidx.annotation.RequiresApi
import com.example.korailreservationapp.R
import com.example.korailreservationapp.databinding.ReservationServiceLayoutBinding
import com.example.korailreservationapp.service.KorailReservationService
import com.example.korailreservationapp.service.data.Ticket
import com.example.korailreservationapp.utils.hide
import com.example.korailreservationapp.utils.show
import com.example.korailreservationapp.utils.toggleVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList

@RequiresApi(Build.VERSION_CODES.N)
class ReservationUi(private val service: KorailReservationService) {

    private lateinit var checkedState: ArrayList<Int>
    private var binding: ReservationServiceLayoutBinding
    private var wm = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
    private var layoutParams = WindowManager.LayoutParams()
    private var expanded = false
    private val adapter = TicketListAdapter { idx, seatType, checked ->
        checkedState[idx] =
            if (checked && seatType == 0)
                checkedState[idx] or (1 shl 0)
            else if (!checked && seatType == 0)
                checkedState[idx] xor (1 shl 0)
            else if (checked && seatType == 1)
                checkedState[idx] or (1 shl 1)
            else
                checkedState[idx] xor (1 shl 1)
    }

    init {
        layoutParams.apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags =
                flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = convertLayoutParamsDp(50F)
            gravity = Gravity.TOP
        }
        binding = ReservationServiceLayoutBinding.inflate(
            LayoutInflater.from(service),
            null, false
        ).apply {
            wm.addView(root, layoutParams)
        }
        initComponents()
    }

    private fun initComponents() {
        binding.run {
            commandButton.setOnClickListener {
                refresh.toggleVisibility()
                exit.toggleVisibility()
                reservation.toggleVisibility()
            }

            exit.setOnClickListener { service.disableSelf() }

            hide.setOnClickListener {
                if (expanded) {
                    refresh.hide()
                    exit.hide()
                    reservation.hide()
                    commandButton.hide()
                    ticketList.hide()
                    layoutParams.height = convertLayoutParamsDp(50F)
                    wm.updateViewLayout(binding.root, layoutParams)
                    expanded = false
                    (it as ImageView).setImageResource(R.drawable.downward)
                } else {
                    ticketList.show()
                    commandButton.show()
                    layoutParams.height = convertLayoutParamsDp(500F)
                    wm.updateViewLayout(binding.root, layoutParams)
                    expanded = true
                    (it as ImageView).setImageResource(R.drawable.upward)
                }
            }

            refresh.setOnClickListener {
                collectData()
            }

            reservation.setOnClickListener {
                //todo : need to implement reservation auto click logic
                for (idx in 0 until checkedState.size) {
                    if (checkedState[idx] > 0) {
                        val ticket = adapter.currentList[idx]
                        val rect = Rect()
                        ticket.seat.getBoundsInScreen(rect)

                        ReservationController.click(
                            rect.centerX().toFloat(),
                            rect.centerY().toFloat(),
                            service
                        )
                    }
                }
            }

            ticketList.adapter = adapter
        }
    }

    fun collectData() {
        CoroutineScope(Dispatchers.Main).launch {
            ReservationController.collectData(service).collectLatest {
                checkedState = MutableList(it.size) { 0 } as ArrayList<Int>
                adapter.submitList(it)
            }
        }
    }

    private fun convertLayoutParamsDp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value, service.resources.displayMetrics
    ).toInt()
}


