package de.openinc.openconnect.pi;

import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import de.openinc.openconnect.OpenConnect;

public class MailMessenger {
	private static MailMessenger me;
	private Properties prop = new Properties();
	private Session session;
	private String fromAddress;
	private String toAddress;

	private Boolean mailingenabled = false;

	private String header = "<p>There was an error in open.CONNECT instance at" + this.getClass().getName() + ".</p>";
	private String footer = "<p>Kind regards,<br />system</p>";

	public MailMessenger() {
	}

	public static MailMessenger getInstance() {
		try {
			if (me == null) {
				me = new MailMessenger();
			}
		} catch (Exception e) {
			OpenConnect.getInstance().logger.info(e.getMessage());
			return null;
		}

		return me;
	}

	public void init(Boolean starttlsenable, String host, String port, String ssltrust, String username,
			String password, String fromAddress, String toAddress, Boolean sslenable) {
		try {
			prop.put("mail.transport.protocol", "smtp");
			prop.put("mail.smtp.auth", true);
			prop.put("mail.smtp.starttls.enable", starttlsenable);
			prop.put("mail.smtp.host", host);
			prop.put("mail.smtp.port", port);
			prop.put("mail.smtp.ssl.trust", ssltrust);
			prop.put("mail.smtp.ssl.enable", sslenable);

			this.fromAddress = fromAddress;
			this.toAddress = toAddress;

			this.session = Session.getInstance(prop, new Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(username, password);
				}
			});

			this.mailingenabled = true;

			OpenConnect.getInstance().logger.info("Mailing enabled");
		} catch (Exception e) {
			this.mailingenabled = false;

			OpenConnect.getInstance().logger.info("Error in initializing mailing");
			OpenConnect.getInstance().logger.info(e.getMessage());
		}
	}

	public void sendMail(String subject, String body) {
		if (!this.mailingenabled) {
			return;
		}

		Message message = new MimeMessage(session);
		try {
			message.setFrom(new InternetAddress(fromAddress));
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress));
			message.setSubject(subject);

			MimeBodyPart mimeBodyPart = new MimeBodyPart();
			mimeBodyPart.setContent(header + body + footer, "text/html; charset=utf-8");

			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(mimeBodyPart);

			message.setContent(multipart);

			Transport.send(message);
		} catch (AddressException e) {
			OpenConnect.getInstance().logger.error(e.getMessage());
		} catch (MessagingException e) {
			OpenConnect.getInstance().logger.error(e.getMessage());
		}
	}

}
