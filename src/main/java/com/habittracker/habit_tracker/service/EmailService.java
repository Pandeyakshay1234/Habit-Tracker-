package com.habittracker.habit_tracker.service;

import com.habittracker.habit_tracker.entity.Habit;
import com.habittracker.habit_tracker.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * EmailService — Service for constructing and dispatching HTML emails via JavaMailSender (SMTP)
 *
 * Key Design Features:
 *   1. MimeMessage & HTML Template: Creates a styled, mobile-responsive HTML notification email.
 *   2. Fail-Safe Error Handling: Catches MessagingException and logs errors with SLF4J
 *      so that a failure sending an email to one user does not interrupt the scheduler for other users.
 */
@Service                                        // Registers this class as a Spring Service Bean
@RequiredArgsConstructor                        // Lombok generates constructor for JavaMailSender
@Slf4j                                          // Lombok provides SLF4J logger instance (log.info, log.error)
public class EmailService {

    // ─────────────────────────────────────────────────────────────
    // DEPENDENCY
    // ─────────────────────────────────────────────────────────────

    // Spring Boot's mail sender abstraction (configured via application.properties)
    private final JavaMailSender mailSender;

    // Sender email address loaded from application.properties (with fallback default)
    @Value("${spring.mail.username:noreply@habittracker.com}")
    private String fromEmail;

    // ─────────────────────────────────────────────────────────────
    // 1. SEND DAILY REMINDER EMAIL
    // ─────────────────────────────────────────────────────────────

    /**
     * Sends an HTML reminder email to a user listing their uncompleted habits for today.
     *
     * @param user the recipient user
     * @param pendingHabits list of habits that have not yet been logged today
     */
    public void sendDailyReminderEmail(User user, List<Habit> pendingHabits) {
        // Guard check: if there are no pending habits or user email is missing, do nothing
        if (user == null || user.getEmail() == null || pendingHabits == null || pendingHabits.isEmpty()) {
            return;
        }

        try {
            // Create a new MIME message instance from JavaMailSender
            MimeMessage message = mailSender.createMimeMessage();

            // MimeMessageHelper simplifies setting subject, recipient, multipart content, and UTF-8 encoding
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Configure email headers
            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject("🔥 Don't break your streak! You have " + pendingHabits.size() + " pending habit(s) today");

            // Build HTML email content
            String htmlContent = buildReminderHtml(user.getName(), pendingHabits);

            // Set HTML body content (second parameter true enables HTML rendering)
            helper.setText(htmlContent, true);

            // Dispatch email asynchronously via SMTP server
            mailSender.send(message);

            // Log successful email dispatch
            log.info("Successfully sent daily reminder email to: {}", user.getEmail());

        } catch (MessagingException e) {
            // Log failure with error message without crashing the background thread
            log.error("Failed to send reminder email to {}: {}", user.getEmail(), e.getMessage());
        } catch (Exception e) {
            // Catch any unexpected SMTP connection/network exceptions
            log.error("Unexpected error sending reminder email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2. HELPER: BUILD RESPONSIVE HTML TEMPLATE
    // ─────────────────────────────────────────────────────────────

    /**
     * Constructs a clean, modern HTML email body with inline CSS styling.
     */
    private String buildReminderHtml(String userName, List<Habit> pendingHabits) {
        // Build list items for each pending habit
        StringBuilder habitListHtml = new StringBuilder();
        for (Habit habit : pendingHabits) {
            habitListHtml.append("<li style='padding: 8px 0; font-size: 15px; color: #333333;'>")
                    .append("<strong>📌 ")
                    .append(escapeHtml(habit.getName()))
                    .append("</strong>");

            if (habit.getDescription() != null && !habit.getDescription().isBlank()) {
                habitListHtml.append(" — <span style='color: #777777;'>")
                        .append(escapeHtml(habit.getDescription()))
                        .append("</span>");
            }

            habitListHtml.append("</li>");
        }

        // Return complete HTML email template with inline styles
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f4f7f6; margin: 0; padding: 20px; }
                    .container { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 12px; padding: 30px; box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
                    .header { text-align: center; border-bottom: 2px solid #f0f0f0; padding-bottom: 20px; }
                    .header h1 { color: #2d3748; margin: 0; font-size: 24px; }
                    .content { padding: 20px 0; }
                    .habit-box { background: #fffaf0; border-left: 4px solid #ed8936; border-radius: 6px; padding: 15px 20px; margin: 20px 0; }
                    .footer { text-align: center; color: #a0aec0; font-size: 13px; border-top: 1px solid #edf2f7; padding-top: 20px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🔥 Habit Tracker Reminder</h1>
                    </div>
                    <div class="content">
                        <p style="font-size: 16px; color: #4a5568;">Hi <strong>%s</strong>,</p>
                        <p style="font-size: 15px; color: #4a5568; line-height: 1.6;">
                            You're doing great on your habit journey! Don't let today slip away.
                            You still have <strong>%d pending habit(s)</strong> to complete before midnight:
                        </p>
                        <div class="habit-box">
                            <ul style="margin: 0; padding-left: 20px;">
                                %s
                            </ul>
                        </div>
                        <p style="font-size: 15px; color: #4a5568; line-height: 1.6;">
                            Log your habits before <strong>11:59 PM</strong> to keep your streak alive! 🔥
                        </p>
                    </div>
                    <div class="footer">
                        <p>Habit Tracker Backend • Automated Daily Reminder</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                userName != null ? escapeHtml(userName) : "Habit Champion",
                pendingHabits.size(),
                habitListHtml.toString()
        );
    }

    // ─────────────────────────────────────────────────────────────
    // 3. HELPER: ESCAPE HTML ENTITIES
    // ─────────────────────────────────────────────────────────────

    /**
     * Escapes basic HTML characters to prevent template injection in emails.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
