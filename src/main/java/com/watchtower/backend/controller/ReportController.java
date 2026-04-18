package com.watchtower.backend.controller;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.watchtower.backend.service.HistoryAnalysisService;
import com.watchtower.backend.service.TrendAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * AJT Unit 9 — PDF Weekly Report Generator.
 *
 * GET  /api/report/weekly      — download PDF report (iText)
 * GET  /api/report/preview     — JSON summary of what the PDF would contain
 */
@Slf4j
@RestController
@RequestMapping("/api/report")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ReportController {

    private final HistoryAnalysisService historyService;
    private final TrendAnalysisService   trendService;

    /**
     * JSON preview of the weekly report data (used before PDF is fully wired on Day 9).
     */
    @GetMapping("/preview")
    public Map<String, Object> getReportPreview() {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("reportTitle",   "WatchTower Weekly Network Report");
        preview.put("generatedAt",   java.time.LocalDateTime.now().toString());
        preview.put("dailyTotals",   historyService.getWeeklyDailyTotals());

        List<Map<String, Object>> peakHoursList = new ArrayList<>();
        historyService.getPeakHoursLastWeek().forEach((hour, traffic) -> {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("hour", String.format("%02d:00", hour));
            h.put("traffic", traffic);
            peakHoursList.add(h);
        });
        preview.put("peakHours",     peakHoursList);

        preview.put("perDevice",     historyService.getPerDeviceSummary());
        preview.put("trend",         trendService.getTrendAnalysis());
        return preview;
    }

    /**
     * PDF generation endpoint — generates weekly report using iText 5.
     * Returns a downloadable PDF file with comprehensive report data.
     */
    @GetMapping("/weekly")
    public ResponseEntity<byte[]> downloadWeeklyPdf() {
        try {
            log.info("ReportController: PDF generation requested");
            
            // Create PDF document
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, baos);
            document.open();
            
            // Title
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 24, Font.BOLD);
            Paragraph title = new Paragraph("WatchTower Weekly Network Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);
            
            // Report metadata
            Font headerFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
            Font normalFont = new Font(Font.FontFamily.HELVETICA, 10);
            
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            
            document.add(new Paragraph("Report Title: WatchTower Weekly Network Report", normalFont));
            document.add(new Paragraph("Generated: " + now.format(formatter), normalFont));
            document.add(new Paragraph("Period: Last 7 Days", normalFont));
            document.add(new Paragraph("\n"));
            
            // Daily Totals Section
            document.add(new Paragraph("Daily Usage Statistics", headerFont));
            
            List<Map<String, Object>> dailyList = historyService.getWeeklyDailyTotals();
            if (dailyList != null && !dailyList.isEmpty()) {
                PdfPTable table = new PdfPTable(3);
                table.setWidthPercentage(100);
                table.setSpacingBefore(10);
                table.setSpacingAfter(15);
                
                // Header row
                PdfPCell headerCell1 = new PdfPCell(new Phrase("Date", headerFont));
                PdfPCell headerCell2 = new PdfPCell(new Phrase("Total Traffic (GB)", headerFont));
                PdfPCell headerCell3 = new PdfPCell(new Phrase("Peak Load (%)", headerFont));
                headerCell1.setBackgroundColor(BaseColor.LIGHT_GRAY);
                headerCell2.setBackgroundColor(BaseColor.LIGHT_GRAY);
                headerCell3.setBackgroundColor(BaseColor.LIGHT_GRAY);
                table.addCell(headerCell1);
                table.addCell(headerCell2);
                table.addCell(headerCell3);
                
                // Data rows
                for (Map<String, Object> day : dailyList) {
                    String date = day.get("date") != null ? day.get("date").toString() : "N/A";
                    Object traffic = day.get("totalTraffic");
                    Object peakLoad = day.get("peakLoad");
                    
                    String trafficStr = traffic != null ? 
                        String.format("%.2f", ((Number) traffic).doubleValue() / (1024 * 1024 * 1024)) : "0";
                    String peakStr = peakLoad != null ? 
                        String.format("%.1f", ((Number) peakLoad).doubleValue()) : "0";
                    
                    table.addCell(new PdfPCell(new Phrase(date, normalFont)));
                    table.addCell(new PdfPCell(new Phrase(trafficStr, normalFont)));
                    table.addCell(new PdfPCell(new Phrase(peakStr, normalFont)));
                }
                document.add(table);
            }
            
            // Peak Hours Section
            document.add(new Paragraph("\nPeak Activity Hours", headerFont));
            Map<Integer, Double> peakData = historyService.getPeakHoursLastWeek();
            if (peakData != null && !peakData.isEmpty()) {
                PdfPTable peakTable = new PdfPTable(2);
                peakTable.setWidthPercentage(100);
                peakTable.setSpacingBefore(10);
                peakTable.setSpacingAfter(15);
                
                PdfPCell hc1 = new PdfPCell(new Phrase("Hour", headerFont));
                PdfPCell hc2 = new PdfPCell(new Phrase("Traffic (MB)", headerFont));
                hc1.setBackgroundColor(BaseColor.LIGHT_GRAY);
                hc2.setBackgroundColor(BaseColor.LIGHT_GRAY);
                peakTable.addCell(hc1);
                peakTable.addCell(hc2);
                
                for (Map.Entry<Integer, Double> entry : peakData.entrySet()) {
                    String hourStr = String.format("%02d:00", entry.getKey());
                    String traffic = String.format("%.0f", entry.getValue());
                    
                    peakTable.addCell(new PdfPCell(new Phrase(hourStr, normalFont)));
                    peakTable.addCell(new PdfPCell(new Phrase(traffic, normalFont)));
                }
                document.add(peakTable);
            }
            
            // Per-Device Summary
            document.add(new Paragraph("\nPer-Device Summary", headerFont));
            List<Map<String, Object>> deviceList = historyService.getPerDeviceSummary();
            if (deviceList != null && !deviceList.isEmpty()) {
                PdfPTable deviceTable = new PdfPTable(4);
                deviceTable.setWidthPercentage(100);
                deviceTable.setSpacingBefore(10);
                deviceTable.setSpacingAfter(15);
                
                PdfPCell dc1 = new PdfPCell(new Phrase("Device Name", headerFont));
                PdfPCell dc2 = new PdfPCell(new Phrase("Type", headerFont));
                PdfPCell dc3 = new PdfPCell(new Phrase("Total Traffic (GB)", headerFont));
                PdfPCell dc4 = new PdfPCell(new Phrase("Avg Load (%)", headerFont));
                dc1.setBackgroundColor(BaseColor.LIGHT_GRAY);
                dc2.setBackgroundColor(BaseColor.LIGHT_GRAY);
                dc3.setBackgroundColor(BaseColor.LIGHT_GRAY);
                dc4.setBackgroundColor(BaseColor.LIGHT_GRAY);
                deviceTable.addCell(dc1);
                deviceTable.addCell(dc2);
                deviceTable.addCell(dc3);
                deviceTable.addCell(dc4);
                
                for (Map<String, Object> device : deviceList) {
                    String name = device.get("deviceName") != null ? device.get("deviceName").toString() : "Unknown";
                    String type = device.get("deviceType") != null ? device.get("deviceType").toString() : "N/A";
                    Object totalTraffic = device.get("totalTraffic");
                    Object avgLoad = device.get("avgLoad");
                    
                    String trafficStr = totalTraffic != null ? 
                        String.format("%.2f", ((Number) totalTraffic).doubleValue()) : "0";
                    String loadStr = avgLoad != null ? 
                        String.format("%.1f", ((Number) avgLoad).doubleValue()) : "0";
                    
                    deviceTable.addCell(new PdfPCell(new Phrase(name, normalFont)));
                    deviceTable.addCell(new PdfPCell(new Phrase(type, normalFont)));
                    deviceTable.addCell(new PdfPCell(new Phrase(trafficStr, normalFont)));
                    deviceTable.addCell(new PdfPCell(new Phrase(loadStr, normalFont)));
                }
                document.add(deviceTable);
            }
            
            // Trend Analysis
            document.add(new Paragraph("\nTrend Analysis", headerFont));
            Map<String, Object> trendData = trendService.getTrendAnalysis();
            if (trendData != null) {
                String direction = trendData.get("direction") != null ? 
                    trendData.get("direction").toString() : "Stable";
                Object change = trendData.get("weeklyChange");
                String changeStr = change != null ? 
                    String.format("%.1f", ((Number) change).doubleValue()) : "0";
                
                document.add(new Paragraph("Overall Trend: " + direction, normalFont));
                document.add(new Paragraph("Week-over-Week Change: " + changeStr + "%", normalFont));
            }
            
            // Footer
            document.add(new Paragraph("\n\nEnd of Report", normalFont));
            document.close();
            
            // Prepare response
            byte[] pdfBytes = baos.toByteArray();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                "watchtower-report-" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf");
            headers.setContentLength(pdfBytes.length);
            
            log.info("ReportController: PDF generated successfully, size: {} bytes", pdfBytes.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
            
        } catch (Exception e) {
            log.error("Error generating PDF report", e);
            return ResponseEntity.status(500)
                    .body(("Error generating PDF: " + e.getMessage()).getBytes());
        }
    }
}
