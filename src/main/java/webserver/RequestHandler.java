package webserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import db.DataBase;
import model.User;
import util.HttpRequestUtils;
import util.IOUtils;

public class RequestHandler extends Thread {
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
            connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.

            /**
             * 요구사항1 - index.html 응답하기
             */
            //BufferedReader 로 헤더 값 읽기
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            //1번째 줄 : 요청방식, 요청 URL, 프로토콜, 버전 (공백으로 나뉨)
            String line = reader.readLine();

            //헤더가 null 일 경우 응답안함
            if (line == null) {
                return;
            }

            String url = HttpRequestUtils.getUrl(line, reader);

            /**
             * 요구사항3 - POST 방식으로 회원가입하기
             */
            Map<String, String> headers = new HashMap<>();
            //헤더 마지막을 확인
            while (!"".equals(line)) {
                log.info("헤더 : {}", line);
                line = reader.readLine();
                String[] token = line.split(": ");
                if (token.length == 2) {
                    headers.put(token[0], token[1]);
                }
            }

            log.debug("Content-Length - {}", headers.get("Content-Length"));

            /**
             * 요구사항2 - GET 방식으로 회원가입하기
             */
            if (url.startsWith("/user/create")) {
                // int index = url.indexOf("?");
                // String requestPath = url.substring(0, index);
                // String params = url.substring(index + 1);
                String params = IOUtils.readData(reader, Integer.parseInt(headers.get("Content-Length")));
                Map<String, String> data = HttpRequestUtils.parseQueryString(params);
                User user = new User(data.get("userId"), data.get("password"), data.get("name"), data.get("email"));
                log.debug("user info - {}", user);
                DataBase.addUser(user);
                // url = "/index.html";

                DataOutputStream dos = new DataOutputStream(out);
                // URL 해당하는 파일을 가지고와서 byte array로 변환 후 body에 넣어준다.
                // byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
                // byte[] body = "Hello World".getBytes();
                response302Header(dos);
            } else if (url.equals("/user/login")) {
                /**
                 * 요구사항5 - 로그인하기
                 */
                String requestBody = IOUtils.readData(reader, Integer.parseInt(headers.get("Content-Length")));
                log.debug("requestBody - {}", requestBody);
                Map<String, String> data = HttpRequestUtils.parseQueryString(requestBody);
                log.debug("userId - {}, pwd - {} ", data.get("userId"), data.get("password"));
                User user = DataBase.getUser(data.get("userId"));
                if (user == null) {
                    DataOutputStream dos = new DataOutputStream(out);
                    response302Header(dos);
                    log.debug("User Not Found");
                }
                if (user.getPassword().equals(data.get("password"))) {
                    DataOutputStream dos = new DataOutputStream(out);
                    response302HeaderWithCookie(dos, "logined=true");
                    log.debug("login success");
                } else {
                    DataOutputStream dos = new DataOutputStream(out);
                    response302Header(dos);
                    log.debug("pwd mismatch");
                }

            } else if (url.equals("/user/list")) {
                /**
                 * 요구사항6 - 사용자 목록 출력
                 */
                Map<String, String> cookies = HttpRequestUtils.parseCookies(headers.get("Cookie"));
                if (cookies.get("logined") == null || !Boolean.parseBoolean(cookies.get("logined"))) {
                    DataOutputStream dos = new DataOutputStream(out);
                    response302Header(dos);
                } else {
                    int idx = 3;

                    Collection<User> userList = DataBase.findAll();
                    StringBuilder sb = new StringBuilder();
                    sb.append("<tr>");
                    for (User user : userList) {
                        sb.append(
                            "<th scope=\"row\"> </th><td>" + user.getUserId() + "</td> <td>" + user.getName()
                                + "</td> <td>" + user.getEmail()
                                + "</td></tr>");
                        idx++;
                    }

                    byte[] body = sb.toString().getBytes();
                    DataOutputStream dos = new DataOutputStream(out);
                    response200Header(dos, body.length);
                    responseBody(dos, body);
                }

            } else if (url.endsWith(".css")) {
                /**
                 * 요구사항7 - CSS 지원하기
                 */
                DataOutputStream dos = new DataOutputStream(out);
                byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
                response200HeaderWithCSS(dos, body.length);
                responseBody(dos, body);
            } else {
                /**
                 * 요구사항4 - 302 status code 적용
                 */
                DataOutputStream dos = new DataOutputStream(out);
                // URL 해당하는 파일을 가지고와서 byte array로 변환 후 body에 넣어준다.
                byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
                // byte[] body = "Hello World".getBytes();
                response200Header(dos, body.length);
                responseBody(dos, body);
            }

        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    /**
     * 요구사항7 - CSS 지원하기
     */
    private void response200HeaderWithCSS(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/css;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    /**
     * 요구사항5 - 로그인하기
     */
    private void response302HeaderWithCookie(DataOutputStream dos, String cookie) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: /index.html\r\n");
            dos.writeBytes("Set-Cookie: " + cookie + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    /**
     * 요구사항4 - 302 status code 적용
     */
    private void response302Header(DataOutputStream dos) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: /index.html\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
