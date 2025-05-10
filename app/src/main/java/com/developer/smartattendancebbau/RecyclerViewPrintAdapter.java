package com.developer.smartattendancebbau;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.pdf.PrintedPdfDocument;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class RecyclerViewPrintAdapter extends PrintDocumentAdapter {
    private final Context context;
    private final RecyclerView recyclerView;

    public RecyclerViewPrintAdapter(Context context, RecyclerView recyclerView) {
        this.context = context;
        this.recyclerView = recyclerView;
    }

    @Override
    public void onLayout(PrintAttributes printAttributes, PrintAttributes printAttributes1, CancellationSignal cancellationSignal, LayoutResultCallback layoutResultCallback, Bundle bundle) {
        if (cancellationSignal.isCanceled()) {
            layoutResultCallback.onLayoutCancelled();
            return;
        }
        PrintDocumentInfo info = new PrintDocumentInfo.Builder("attendance.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build();
        layoutResultCallback.onLayoutFinished(info, true);
    }

    @Override
    public void onWrite(PageRange[] pageRanges, ParcelFileDescriptor parcelFileDescriptor, CancellationSignal cancellationSignal, WriteResultCallback writeResultCallback) {
        PrintedPdfDocument pdfDocument = new PrintedPdfDocument(context, new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(new PrintAttributes.Resolution("pdf", Context.PRINT_SERVICE, 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build());

        List<Student> studentList = StudentAdapter.getStudentList();
        int pageWidth = 595;  // A4 width in points (8.3 inches * 72)
        int pageHeight = 842; // A4 height in points (11.7 inches * 72)
        int rowHeight = 50;
        int startY = 50;

        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setTextSize(12);
        paint.setStyle(Paint.Style.STROKE);

        int rowsPerPage = (pageHeight - 100) / rowHeight;
        int pageCount = (int) Math.ceil(studentList.size() / (float) rowsPerPage);

        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            PdfDocument.Page page = pdfDocument.startPage(pageIndex);
            Canvas canvas = page.getCanvas();

            // Draw headers
            int x1 = 10, x2 = 150, x3 = 300, x4 = 450;
            canvas.drawText("Name", x1 + 10, startY, paint);
            canvas.drawText("Roll No", x2 + 10, startY, paint);
            canvas.drawText("Status", x3 + 10, startY, paint);
            canvas.drawText("Date", x4 + 10, startY, paint);

            // Draw header borders
            canvas.drawRect(x1, startY - 20, x2, startY + 10, paint);
            canvas.drawRect(x2, startY - 20, x3, startY + 10, paint);
            canvas.drawRect(x3, startY - 20, x4, startY + 10, paint);
            canvas.drawRect(x4, startY - 20, pageWidth - 10, startY + 10, paint);

            int y = startY + 30;
            for (int i = pageIndex * rowsPerPage; i < Math.min((pageIndex + 1) * rowsPerPage, studentList.size()); i++) {
                Student student = studentList.get(i);

                // Draw text
                canvas.drawText(student.getName(), x1 + 10, y, paint);
                canvas.drawText(student.getRollNumber(), x2 + 10, y, paint);
                canvas.drawText(student.getStatus(), x3 + 10, y, paint);
                canvas.drawText(student.getDate(), x4 + 10, y, paint);

                // Draw borders
                canvas.drawRect(x1, y - 20, x2, y + 10, paint);
                canvas.drawRect(x2, y - 20, x3, y + 10, paint);
                canvas.drawRect(x3, y - 20, x4, y + 10, paint);
                canvas.drawRect(x4, y - 20, pageWidth - 10, y + 10, paint);

                y += rowHeight;
            }

            pdfDocument.finishPage(page);
        }

        try {
            pdfDocument.writeTo(new FileOutputStream(parcelFileDescriptor.getFileDescriptor()));
            writeResultCallback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
        } catch (IOException e) {
            writeResultCallback.onWriteFailed(e.toString());
        } finally {
            pdfDocument.close();
        }
    }
    }



