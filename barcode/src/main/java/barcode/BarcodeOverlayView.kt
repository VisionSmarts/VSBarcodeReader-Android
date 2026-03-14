//
//  BarcodeOverlayView.kt
//
//  Copyright 2011-2026 Vision Smarts SRL. All rights reserved.
//

package com.visionsmarts.barcode

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.util.AttributeSet
import android.util.Log
import android.view.View

class BarcodeOverlayView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 6.0f
    }

    private var cornerPointsList: List<Array<Point>> = listOf()
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private var lensFrontFacing: Boolean = false
    private var rotation: Int = 0

    fun setCorners(
        corners: List<Array<Point>>,
        imageWidth: Int,
        imageHeight: Int,
        lensFrontFacing: Boolean,
        rotation: Int
    ) {
        this.cornerPointsList = corners
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.lensFrontFacing = lensFrontFacing
        this.rotation = rotation
        invalidate() // Tell the view to redraw itself
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (points in cornerPointsList) {
            if (points.size < 4) continue

            val path = Path()
            val firstAdjustedPoint = adjustPoint(points[0])
            path.moveTo(firstAdjustedPoint.x.toFloat(), firstAdjustedPoint.y.toFloat())

            for (i in 1..3) {
                val adjustedPoint = adjustPoint(points[i])
                path.lineTo(adjustedPoint.x.toFloat(), adjustedPoint.y.toFloat())
            }
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    private fun adjustPoint(point: Point): Point {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // From image sensor orientation to screen orientation
        var rotatedImageWidth = imageWidth
        var rotatedImageHeight = imageHeight
        if (rotation==90 || rotation==270) {
            rotatedImageWidth = imageHeight
            rotatedImageHeight = imageWidth
        }

        var rotatedPoint = Point(point.x, point.y)
        when (rotation) {
            90 -> {
                rotatedPoint = Point(imageHeight - point.y, point.x)
            }
            180 -> {
                rotatedPoint = Point(imageWidth - point.x, imageHeight - point.y)
            }
            270 -> {
                rotatedPoint = Point(point.y, imageWidth - point.x)
            }
        }

        val scaleX = viewWidth / rotatedImageWidth
        val scaleY = viewHeight / rotatedImageHeight

        val scale = maxOf(scaleX, scaleY)

        val offsetX = (viewWidth - (rotatedImageWidth * scale)) / 2.0
        val offsetY = (viewHeight - (rotatedImageHeight * scale)) / 2.0

        var scaledX = rotatedPoint.x * scale + offsetX
        var scaledY = rotatedPoint.y * scale + offsetY

        if (lensFrontFacing) {
            scaledX = viewWidth - scaledX
        }

        return Point(scaledX.toInt(), scaledY.toInt())
    }
}