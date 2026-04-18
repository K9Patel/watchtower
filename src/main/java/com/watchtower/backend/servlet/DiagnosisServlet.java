package com.watchtower.backend.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchtower.backend.repository.AlertRepository;
import com.watchtower.backend.service.AnalysisService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AJT Unit 6 — Raw HttpServlet (no Spring MVC involved):
 *
 * This is a traditional Java EE servlet registered via @WebServlet.
 * It does NOT go through Spring's DispatcherServlet — it is a direct
 * mapping handled by the embedded Tomcat container.
 *
 * Contrast with @RestController (Spring MVC, Unit 7):
 *   - @RestController → DispatcherServlet → HandlerMapping → Controller
 *   - @WebServlet     → Tomcat → doGet() / doPost() directly
 *
 * URL: GET http://localhost:8080/diagnosis
 *
 * Retrieves Spring beans via WebApplicationContextUtils (since this
 * servlet lives outside Spring's normal DI container).
 */
@WebServlet(urlPatterns = "/diagnosis", name = "DiagnosisServlet")
public class DiagnosisServlet extends HttpServlet {

    private transient AnalysisService  analysisService;
    private transient AlertRepository  alertRepository;
    private transient ObjectMapper     objectMapper;

    @Override
    public void init() {
        // AJT Unit 6: retrieve Spring beans from the Spring application context
        // This is how a raw servlet accesses Spring-managed beans
        WebApplicationContext ctx =
                WebApplicationContextUtils.getWebApplicationContext(getServletContext());

        if (ctx != null) {
            analysisService = ctx.getBean(AnalysisService.class);
            alertRepository = ctx.getBean(AlertRepository.class);
            objectMapper    = ctx.getBean(ObjectMapper.class);
        }
    }

    /**
     * AJT Unit 6: doGet() is the HTTP GET handler.
     * In Spring MVC this is handled by @GetMapping — here it is explicit.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // Set response headers explicitly — Spring MVC does this automatically
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        Map<String, Object> diagnosis = new LinkedHashMap<>();
        diagnosis.put("servlet",   "DiagnosisServlet (raw HttpServlet — AJT Unit 6)");
        diagnosis.put("timestamp", LocalDateTime.now().toString());

        if (analysisService != null) {
            diagnosis.put("totalLoadPercent",  analysisService.getTotalLoad());
            diagnosis.put("trafficBreakdown",  analysisService.getTrafficBreakdown());
        } else {
            diagnosis.put("warning", "Spring context not yet initialised");
        }

        if (alertRepository != null) {
            diagnosis.put("unresolvedAlerts", alertRepository.countByIsResolvedFalse());
        }

        // AJT Unit 6: write JSON to the response OutputStream manually
        PrintWriter writer = response.getWriter();
        if (objectMapper != null) {
            writer.write(objectMapper.writeValueAsString(diagnosis));
        } else {
            writer.write("{\"error\": \"ObjectMapper not available\"}");
        }
        writer.flush();
    }

    /**
     * AJT Unit 6: doPut() — demonstrates handling a non-GET method in a raw servlet.
     * Returns 405 Method Not Allowed for anything other than GET.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"Use GET /diagnosis\"}");
    }
}
