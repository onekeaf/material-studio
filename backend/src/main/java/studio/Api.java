package studio;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class Api {
  private final Store db;
  private final Pipeline pipeline;
  private final TransactionTemplate tx;
  private final Path root;
  private final boolean demo;
  private final ObjectMapper json;

  @Value("${studio.cookie-secure:false}")
  private boolean cookieSecure;

  public Api(
      Store db,
      Pipeline pipeline,
      PlatformTransactionManager manager,
      @Value("${studio.data}") String dir,
      @Value("${studio.demo}") boolean demo,
      ObjectMapper json)
      throws IOException {
    this.db = db;
    this.pipeline = pipeline;
    this.tx = new TransactionTemplate(manager);
    this.root = Path.of(dir);
    this.demo = demo;
    this.json = json;
    Files.createDirectories(root);
  }

  String uuid() {
    return UUID.randomUUID().toString();
  }

  String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  String owner(HttpServletRequest r) {
    if (r.getCookies() != null)
      for (Cookie c : r.getCookies())
        if (c.getName().equals("studio")) {
          String v = db.visitor(hash(c.getValue()));
          if (v != null) return v;
        }
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新建立访客身份");
  }

  @PostMapping("/session")
  Map<String, Object> session(HttpServletRequest r, HttpServletResponse response) {
    try {
      return sessionInfo(owner(r));
    } catch (ResponseStatusException e) {
      return identity(uuid(), uuid(), response);
    }
  }

  Map<String, Object> identity(String id, String token, HttpServletResponse response) {
    if (db.visitor(hash(token)) == null) db.visitorAdd(id, hash(token));
    response.addHeader(
        "Set-Cookie",
        ResponseCookie.from("studio", token)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Lax")
            .path("/")
            .maxAge(2592000)
            .build()
            .toString());
    return sessionInfo(id);
  }

  Map<String, Object> sessionInfo(String id) {
    return Map.of("id", id, "demoEnabled", demo);
  }

  @PostMapping("/dev/session/switch")
  Map<String, Object> switchUser(
      @RequestBody Map<String, String> data, HttpServletResponse response) {
    if (!demo) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    String p = data.get("persona");
    if (p == null || !Set.of("A", "B").contains(p))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效访客");
    return identity("演示访客" + p, "local-demo-visitor-" + p, response);
  }

  @GetMapping("/me")
  Map<String, Object> me(HttpServletRequest r) {
    return sessionInfo(owner(r));
  }

  @GetMapping("/templates")
  java.util.List<Map<String, Object>> templates(@RequestParam(required = false) String category) {
    return db.templates().stream()
        .filter(t -> category == null || category.equals(t.get("category")))
        .map(this::templateDto)
        .toList();
  }

  @GetMapping("/categories")
  java.util.List<String> categories() {
    return db.templates().stream().map(t -> t.get("category").toString()).distinct().toList();
  }

  Map<String, Object> templateDto(Map<String, Object> t) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (String field : java.util.List.of("id", "title", "category", "description"))
      out.put(field, t.get(field));
    out.put("requiredImageCount", t.get("required_image_count"));
    out.put("acceptedMimeTypes", java.util.List.of("image/jpeg", "image/png"));
    out.put("maxFileSizeBytes", 5242880);
    out.put("coverUrl", "/api/public/cover/" + t.get("id"));
    return out;
  }

  Map<String, Object> findTemplate(String id) {
    Map<String, Object> t = db.template(id);
    if (t == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    return t;
  }

  @GetMapping("/templates/{id}")
  Map<String, Object> template(@PathVariable String id) {
    return templateDto(findTemplate(id));
  }

  @GetMapping(value = "/public/cover/{id}", produces = "image/png")
  byte[] cover(@PathVariable String id) throws IOException {
    return artwork(findTemplate(id));
  }

  byte[] artwork(Map<String, Object> t) throws IOException {
    BufferedImage image = new BufferedImage(720, 1080, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    Color base = Color.decode(t.get("color").toString());
    g.setPaint(new GradientPaint(0, 0, base.brighter(), 720, 1080, base.darker().darker()));
    g.fillRect(0, 0, 720, 1080);
    g.setColor(new Color(255, 244, 210, 210));
    g.fillOval(390, 110, 210, 210);
    g.setColor(base.darker());
    String category = t.get("category").toString();
    if (category.equals("宠物")) {
      g.setColor(new Color(244, 224, 187));
      g.fillOval(115, 390, 490, 400);
      g.fillPolygon(new int[] {130, 155, 310}, new int[] {530, 285, 440}, 3);
      g.fillPolygon(new int[] {410, 565, 590}, new int[] {440, 285, 530}, 3);
      g.setColor(base.darker().darker());
      g.fillOval(235, 545, 28, 55);
      g.fillOval(450, 545, 28, 55);
      g.fillOval(340, 625, 40, 25);
      g.setStroke(new BasicStroke(4));
      g.drawLine(270, 650, 110, 620);
      g.drawLine(270, 675, 100, 690);
      g.drawLine(450, 650, 610, 620);
      g.drawLine(450, 675, 620, 690);
    } else if (category.equals("人像")) {
      g.setColor(base.darker().darker());
      g.fillOval(105, 590, 500, 650);
      g.setColor(new Color(236, 197, 161));
      g.fillOval(235, 310, 250, 350);
      g.setColor(base.darker().darker());
      g.fillArc(215, 280, 290, 280, 0, 190);
      g.setColor(new Color(236, 197, 161));
      g.fillRect(312, 610, 98, 90);
    } else {
      g.fillPolygon(new int[] {-100, 250, 500, 850}, new int[] {850, 390, 790, 1080}, 4);
      if (t.get("id").equals("postcard")) {
        g.setColor(new Color(85, 180, 191));
        g.fillRect(0, 690, 720, 390);
      }
    }
    g.setColor(new Color(255, 255, 255, 40));
    for (int i = 0; i < 12; i++) g.drawOval(-300, 600 + i * 25, 1400, 350);
    g.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  @PostMapping("/assets")
  Map<String, Object> upload(
      @RequestParam String templateId, @RequestParam MultipartFile file, HttpServletRequest r)
      throws IOException {
    String user = owner(r);
    findTemplate(templateId);
    if (file.getSize() > 5242880)
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "图片不能超过 5 MiB");
    byte[] b = file.getBytes();
    boolean png = b.length > 8 && b[0] == (byte) 137 && b[1] == 80;
    boolean jpg = b.length > 3 && b[0] == (byte) 255 && b[1] == (byte) 216;
    if ((!png && !jpg) || ImageIO.read(new ByteArrayInputStream(b)) == null)
      throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请选择有效的 JPEG 或 PNG 图片");
    String id = uuid();
    Files.write(root.resolve(id), b);
    String mime = png ? "image/png" : "image/jpeg";
    db.assetAdd(id, user, mime, b.length, id);
    return Map.of(
        "assetId",
        id,
        "mimeType",
        mime,
        "sizeBytes",
        b.length,
        "previewUrl",
        "/api/assets/" + id + "/content");
  }

  @GetMapping("/assets/{id}/content")
  ResponseEntity<byte[]> asset(@PathVariable String id, HttpServletRequest r) throws IOException {
    Map<String, Object> a = db.asset(id, owner(r));
    if (a == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(a.get("mime").toString()))
        .cacheControl(CacheControl.noStore())
        .body(Files.readAllBytes(root.resolve(a.get("path").toString())));
  }

  public record Create(
      String templateId, java.util.List<String> assetIds, Map<String, Object> simulation) {}

  @PostMapping("/tasks")
  ResponseEntity<Map<String, Object>> create(@RequestBody Create request, HttpServletRequest r)
      throws Exception {
    String user = owner(r);
    Map<String, Object> t = findTemplate(request.templateId());
    if (request.assetIds() == null
        || request.assetIds().size() != ((Number) t.get("required_image_count")).intValue()
        || new HashSet<>(request.assetIds()).size() != request.assetIds().size())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片数量不符合要求");
    for (String a : request.assetIds())
      if (db.asset(a, user) == null)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "素材不存在或不属于当前访客");
    if (request.simulation() != null && !demo)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模拟控制未启用");
    Map<String, Object> s = request.simulation() == null ? Map.of() : request.simulation();
    if (s.containsKey("delaysMs") && !(s.get("delaysMs") instanceof Map<?, ?>))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "delaysMs 必须为耗时配置对象");
    String fail = Objects.toString(s.get("failStage"), "");
    if (!fail.isEmpty() && !Pipeline.STAGES.contains(fail))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "失败阶段无效");
    Pipeline.Simulation config =
        new Pipeline.Simulation(
            fail,
            delay(s, "assetCheck", 1200),
            delay(s, "generate", 6000),
            delay(s, "finalize", 1200));
    String id = uuid(), snapshot = json.writeValueAsString(config);
    tx.executeWithoutResult(
        x -> {
          db.create(id, user, request.templateId(), t.get("title").toString(), snapshot);
          for (int i = 0; i < request.assetIds().size(); i++)
            db.bind(id, request.assetIds().get(i), i);
          for (int i = 0; i < 3; i++) db.stepAdd(id, Pipeline.STAGES.get(i), i);
        });
    try {
      pipeline.submit(
          id,
          config,
          stage -> {
            if (stage.equals("ASSET_CHECK"))
              for (String a : request.assetIds()) {
                Map<String, Object> asset = db.asset(a, user);
                if (asset == null
                    || !Files.isRegularFile(root.resolve(asset.get("path").toString())))
                  throw new IOException("素材文件不可用");
              }
            if (stage.equals("FINALIZE")) Files.write(root.resolve(id + ".png"), artwork(t));
          });
    } catch (java.util.concurrent.RejectedExecutionException e) {
      db.update(id, "FAILED", "WAITING", "队列已满，请稍后新建任务", null);
    }
    return ResponseEntity.accepted().body(dto(db.task(id, user)));
  }

  long delay(Map<String, Object> s, String name, long fallback) {
    Object delays = s.get("delaysMs");
    Object value = delays instanceof Map<?, ?> m ? m.get(name) : null;
    if (value == null) return fallback;
    if (!(value instanceof Number n)
        || n.doubleValue() != n.longValue()
        || n.longValue() < 0
        || n.longValue() > 60000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "耗时须为 0～60000 毫秒整数");
    return n.longValue();
  }

  Map<String, Object> dto(Map<String, Object> row) {
    Map<String, Object> m = responseFields(row);
    if ("SUCCEEDED".equals(row.get("status")))
      m.put("resultUrl", "/api/tasks/" + row.get("id") + "/result");
    return m;
  }

  Map<String, Object> responseFields(Map<String, Object> row) {
    Map<String, Object> m = new LinkedHashMap<>();
    row.forEach(
        (k, v) -> {
          if (!Set.of("visitor_id", "result_path", "simulation").contains(k)) {
            StringBuilder key = new StringBuilder();
            boolean up = false;
            for (char c : k.toCharArray()) {
              if (c == '_') up = true;
              else {
                key.append(up ? Character.toUpperCase(c) : c);
                up = false;
              }
            }
            m.put(key.toString(), v);
          }
        });
    return m;
  }

  @GetMapping("/tasks/{id}")
  Map<String, Object> task(@PathVariable String id, HttpServletRequest r) {
    Map<String, Object> t = db.task(id, owner(r));
    if (t == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
    Map<String, Object> m = dto(t);
    m.put("steps", db.steps(id).stream().map(this::responseFields).toList());
    return m;
  }

  @GetMapping("/me/tasks")
  java.util.List<Map<String, Object>> history(
      HttpServletRequest r,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    if (limit < 1 || limit > 100 || offset < 0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
    return db.historyPage(owner(r), limit, offset).stream().map(this::dto).toList();
  }

  @GetMapping("/me/tasks/active")
  java.util.List<Map<String, Object>> active(HttpServletRequest r) {
    return db.history(owner(r)).stream()
        .filter(t -> Set.of("QUEUED", "RUNNING").contains(t.get("status")))
        .map(this::dto)
        .toList();
  }

  @GetMapping(value = "/tasks/{id}/result", produces = "image/png")
  ResponseEntity<byte[]> result(@PathVariable String id, HttpServletRequest r) throws IOException {
    Map<String, Object> t = db.task(id, owner(r));
    if (t == null || !"SUCCEEDED".equals(t.get("status")))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(Files.readAllBytes(root.resolve(t.get("result_path").toString())));
  }

  @GetMapping("/health")
  Map<String, String> health() {
    db.templates();
    return Map.of("status", "ok");
  }
}
