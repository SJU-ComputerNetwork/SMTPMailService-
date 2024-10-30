import java.io.*;
import java.nio.file.Files;
import java.util.Base64;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.swing.JOptionPane;

public class MailService {
	String smtpServer = "smtp.naver.com";
    int port = 465;
    
    public SSLSocket socket;
    public BufferedReader reader;
    public PrintWriter writer;
    
    MailService(){
    	try {
    		socket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(smtpServer, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
            
            String response = reader.readLine();
            if (!response.startsWith("220")) {
                showErrorMessage("SMTP 서버에 연결할 수 없습니다." + response);
                return;
            }
    	}
    	catch(Exception e){
    		showErrorMessage("SMTP 서버에 연결할 수 없습니다." + e.toString());
    	}
    }
    
    
    public boolean loginToServer(String id, String password) {
    	try {
            writer.println("AUTH LOGIN");
            writer.flush();
            checkResponse(reader.readLine(), "현재 명령어를 전송할 수 없습니다. SMTP 연결을 확인해주세요.");

            // 아이디 전송
            String encodedId = Base64.getEncoder().encodeToString(id.getBytes());
            writer.println(encodedId);
            writer.flush();
            checkResponse(reader.readLine(), "아이디를 확인해 주세요.");

            // 비밀번호 전송
            String encodedPassword = Base64.getEncoder().encodeToString(password.getBytes());
            writer.println(encodedPassword);
            writer.flush();
            String response = reader.readLine();

            // 로그인 실패
            if (response.startsWith("535")) { 
            	System.out.println(response);
                showErrorMessage("로그인을 할 수 없습니다. \n 1. 비밀번호를 확인해주세요. \n 2. 네이버에서 SMTP 사용 허가를 설정해주세요. \n 3. 2단계 인증이 설정된 경우, 별도의 어플리케이션 비밀번호가 필요합니다.");
                return false;
            }
            
            // 다른 이유로 실패 시, 메세지를 통해 확인
            checkResponse(response, "로그인 할 수 없습니다. 로그를 확인해주세요.");
            return true;
            
        } catch (IOException e) {
        	System.out.println(e.toString());
            showErrorMessage("로그인 중 오류가 발생했습니다. 다시 시도해 주세요.");
            return false;
        }
    }
    
    public boolean sendMail(String _sender, String _receiver, String _subject, String _content, File[] _attachedFile) {
    	String sender = _sender;
        String receiver = _receiver;
        String subject = _subject;
        String content = _content;
        File[] attachedFile = _attachedFile;
        try {
        	// 발신자 설정
            writer.write("MAIL FROM: <" + sender + ">\n");
            writer.flush();
            checkResponse(reader.readLine(), "발신자 설정에 실패했습니다.");

            // 수신자 설정
            String[] receivers = receiver.split(",");
            for (String rc : receivers) {
            	rc = rc.trim();
                if (!rc.isEmpty()) {
                    writer.write("RCPT TO:<" + rc + ">\n");
                    writer.flush();
                    checkResponse(reader.readLine(), rc + "- 수신자 주소를 찾을 수 없습니다.");
                }
            }

            // 데이터 전송 시작
            writer.write("DATA\n");
            writer.flush();
            checkResponse(reader.readLine(), "데이터 전송 연결에 실패했습니다.");

            // 이메일 헤더
            writer.write("From: " + sender + "\r\n");
            writer.write("To: " + String.join(", ", receivers) + "\r\n");
            writer.write("Subject: " + subject + "\r\n");
            writer.write("Content-Type: multipart/mixed; boundary=frontier\r\n");
            writer.write("\r\n");

            // 이메일 본문
            writer.write("--frontier\r\n");
            writer.write("Content-Type: text/plain\r\n");
            writer.write("\r\n");
            writer.write(content + "\r\n");
            
            
            // 첨부 파일 전송
            for (File file : attachedFile) {
                writer.write("--frontier\r\n");
                writer.write("Content-Type: application/octet-stream\r\n");
                writer.write("Content-Transfer-Encoding: base64\r\n");
                writer.write("Content-Disposition: attachment; filename=\"" + file.getName() + "\"\r\n");
                writer.write("\r\n");

                try {
                    byte[] fileContent = Files.readAllBytes(file.toPath());
                    String encodedFile = Base64.getEncoder().encodeToString(fileContent);
                    writer.write(encodedFile + "\r\n");
                } catch (Exception e) {
                    throw new Exception("첨부 파일 전송 중 오류가 발생했습니다: " + file.getName());
                }
            }
            
            
            // 이메일 송신 종료
            writer.write("--frontier--\r\n");
            writer.write(".\r\n");
            writer.flush();
            showDialogMessage("성공적으로 메일을 전송하였습니다.");
            checkResponse(reader.readLine(), "메시지 전송에 실패했습니다.");
        }
        catch(Exception e){
        	showErrorMessage(e.toString());
        }
        return true;
    }
    
    
    
    // 응답 메세지를 확인한 후, n-smtp의 에러 메시지인 4나 5로 시작하는 코드이면 예외를 던지고 에러 메세지를 출력
    private static void checkResponse(String response, String errorMessage) throws IOException {
        if (response == null || response.startsWith("4") || response.startsWith("5")) {
            throw new IOException(errorMessage + ", " + response);
        }
    }
    
    
    private static void showErrorMessage(String message) {
        JOptionPane.showMessageDialog(null, message, "error", JOptionPane.WARNING_MESSAGE);
    }
    
    private static void showDialogMessage(String message) {
        JOptionPane.showMessageDialog(null, message, "completed", JOptionPane.INFORMATION_MESSAGE);
    }
}
