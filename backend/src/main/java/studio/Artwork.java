package studio;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;

/** Original local illustrations; no remote images or processing of uploaded photos. */
final class Artwork {
  private Artwork() {}

  static byte[] render(Map<String, Object> template) throws IOException {
    BufferedImage image = new BufferedImage(720, 1080, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    String id = template.get("id").toString();
    Color base = Color.decode(template.get("color").toString());
    g.setPaint(new GradientPaint(0, 0, base.brighter(), 400, 1080, base.darker()));
    g.fillRect(0, 0, 720, 1080);
    switch (id) {
      case "cat", "window-cat" -> cat(g, base, id.equals("window-cat"));
      case "pet", "garden-pet" -> dog(g, base, id.equals("garden-pet"));
      case "portrait", "sunset-portrait" -> portrait(g, base, id.equals("sunset-portrait"));
      case "duet" -> duet(g, base);
      case "city" -> city(g, base);
      case "postcard", "island" -> coast(g, base, id.equals("island"));
      default -> mountains(g, base, id.equals("forest"));
    }
    g.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  private static void sun(Graphics2D g, int x, int y, int size) {
    g.setColor(new Color(255, 230, 173, 210));
    g.fillOval(x, y, size, size);
  }

  private static void cat(Graphics2D g, Color base, boolean window) {
    if (window) {
      g.setColor(base.darker());
      g.fillRoundRect(70, 55, 580, 770, 260, 260);
      g.setColor(new Color(194, 214, 211));
      g.fillRoundRect(92, 78, 536, 700, 235, 235);
      g.setColor(base.darker());
      g.fillRect(350, 80, 16, 700);
      g.fillRect(90, 380, 540, 16);
    }
    sun(g, window ? 430 : 470, 110, 130);
    g.setColor(new Color(74, 71, 69));
    g.fillOval(75, 850, 570, 150);
    g.setColor(new Color(226, 187, 131));
    g.fillOval(170, 570, 390, 410);
    g.setStroke(new BasicStroke(65, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.drawArc(74, 720, 280, 210, 70, 210);
    g.setColor(new Color(247, 224, 183));
    g.fillOval(135, 350, 455, 385);
    g.fillPolygon(new int[] {150, 155, 330}, new int[] {470, 230, 380}, 3);
    g.fillPolygon(new int[] {390, 570, 580}, new int[] {380, 230, 470}, 3);
    g.setColor(new Color(217, 154, 143));
    g.fillPolygon(new int[] {185, 190, 277}, new int[] {410, 305, 380}, 3);
    g.fillPolygon(new int[] {445, 540, 545}, new int[] {380, 305, 410}, 3);
    g.setColor(new Color(70, 61, 48));
    g.fillOval(258, 505, 25, 47);
    g.fillOval(445, 505, 25, 47);
    g.fillOval(345, 570, 35, 22);
    g.setStroke(new BasicStroke(4));
    for (int i = 0; i < 3; i++) {
      g.drawLine(285, 595 + i * 15, 130, 575 + i * 32);
      g.drawLine(440, 595 + i * 15, 595, 575 + i * 32);
    }
  }

  private static void dog(Graphics2D g, Color base, boolean garden) {
    sun(g, 490, 90, 130);
    g.setColor(base.darker());
    g.fillOval(-200, 830, 1200, 450);
    if (garden) {
      for (int i = 0; i < 7; i++) {
        int x = 40 + i * 103, y = 600 + i % 3 * 110;
        g.setColor(new Color(67, 100, 68));
        g.fillRect(x, y, 8, 300);
        g.setColor(new Color(248, 216, 172));
        g.fillOval(x - 23, y - 23, 55, 55);
        g.setColor(new Color(190, 117, 93));
        g.fillOval(x - 7, y - 7, 22, 22);
      }
    }
    g.setColor(new Color(233, 220, 193));
    g.fillOval(180, 600, 360, 440);
    g.setColor(new Color(160, 123, 92));
    g.fillOval(100, 325, 140, 370);
    g.fillOval(480, 325, 140, 370);
    g.setColor(new Color(252, 240, 215));
    g.fillOval(168, 330, 385, 390);
    g.setColor(new Color(56, 48, 42));
    g.fillOval(260, 470, 30, 42);
    g.fillOval(435, 470, 30, 42);
    g.fillOval(332, 540, 65, 43);
    g.setColor(new Color(207, 132, 120));
    g.fillRoundRect(345, 590, 40, 65, 30, 30);
    g.setColor(new Color(73, 108, 133));
    g.fillPolygon(new int[] {210, 510, 360}, new int[] {680, 680, 830}, 3);
  }

  private static void person(Graphics2D g, int x, int y, double scale, Color coat, Color hair) {
    Graphics2D p = (Graphics2D) g.create();
    p.translate(x, y);
    p.scale(scale, scale);
    p.setColor(coat);
    p.fillOval(-205, 230, 410, 650);
    p.setColor(new Color(233, 190, 154));
    p.fillRect(-45, 175, 90, 125);
    p.setColor(hair);
    p.fillOval(-140, -90, 280, 360);
    p.setColor(new Color(244, 204, 168));
    p.fillOval(-108, -35, 216, 300);
    p.setColor(hair);
    p.fillArc(-138, -90, 280, 240, 0, 195);
    p.setColor(new Color(85, 61, 53));
    p.fillOval(-55, 105, 15, 10);
    p.fillOval(40, 105, 15, 10);
    p.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    p.setColor(new Color(175, 98, 87));
    p.drawArc(-20, 155, 45, 25, 190, 160);
    p.dispose();
  }

  private static void portrait(Graphics2D g, Color base, boolean sunset) {
    sun(g, sunset ? 70 : 420, sunset ? 160 : 100, sunset ? 350 : 185);
    g.setColor(new Color(244, 218, 174, 65));
    g.fill(new Path2D.Double(new Polygon(new int[] {720, 250, 720}, new int[] {0, 1080, 1080}, 3)));
    person(g, 355, 375, 1.3, base.darker().darker(), new Color(64, 43, 38));
    g.setColor(new Color(220, 191, 130));
    g.setStroke(new BasicStroke(5));
    g.drawArc(110, 500, 480, 550, 220, 80);
  }

  private static void duet(Graphics2D g, Color base) {
    sun(g, 265, 90, 185);
    g.setColor(new Color(243, 218, 197, 65));
    g.fillOval(-160, 690, 1100, 650);
    person(g, 225, 405, 0.95, new Color(222, 181, 150), new Color(75, 45, 42));
    person(g, 490, 435, 0.95, base.darker().darker(), new Color(49, 43, 52));
  }

  private static void mountains(Graphics2D g, Color base, boolean forest) {
    sun(g, 450, 100, 150);
    g.setColor(new Color(255, 255, 255, 65));
    g.fillRoundRect(90, 260, 320, 30, 30, 30);
    g.fillRoundRect(420, 315, 210, 22, 22, 22);
    g.setColor(base.darker());
    g.fillPolygon(new int[] {-140, 270, 680}, new int[] {850, 310, 850}, 3);
    g.setColor(new Color(229, 227, 204));
    g.fillPolygon(new int[] {194, 270, 350, 303, 263, 232}, new int[] {410, 310, 415, 393, 423, 390}, 6);
    g.setColor(base.darker().darker());
    g.fillPolygon(new int[] {150, 560, 920}, new int[] {940, 450, 940}, 3);
    g.setColor(new Color(126, 166, 142));
    g.fillOval(-450, 760, 1400, 650);
    g.setColor(new Color(217, 204, 160));
    g.fillPolygon(new int[] {280, 325, 440, 365}, new int[] {1080, 1080, 820, 850}, 4);
    if (forest) for (int i = 0; i < 10; i++) {
      int x = i * 85 - 60, y = 670 + i % 3 * 75;
      g.setColor(base.darker().darker());
      g.fillRect(x + 29, y, 12, 250);
      g.fillPolygon(new int[] {x - 25, x + 35, x + 95}, new int[] {y + 150, y - 110, y + 150}, 3);
    }
  }

  private static void coast(Graphics2D g, Color base, boolean island) {
    sun(g, 465, 110, 160);
    g.setColor(new Color(53, 114, 135));
    g.fillPolygon(new int[] {-150, 190, 480}, new int[] {650, 350, 650}, 3);
    g.setColor(new Color(72, 173, 185));
    g.fillRect(0, 610, 720, 470);
    g.setColor(new Color(236, 215, 170));
    g.fill(new Ellipse2D.Double(island ? 125 : -300, 860, 900, 500));
    g.setColor(new Color(242, 246, 220, 110));
    g.setStroke(new BasicStroke(5));
    for (int i = 0; i < 8; i++) g.drawArc(-140, 630 + i * 47, 1000, 140, 10, 150);
    g.setColor(new Color(251, 240, 212));
    g.fillPolygon(new int[] {350, 350, 450}, new int[] {635, 410, 635}, 3);
    g.setColor(base.darker().darker());
    g.fillPolygon(new int[] {315, 465, 430, 345}, new int[] {645, 645, 680, 680}, 4);
    if (island) {
      g.setColor(new Color(88, 77, 51));
      g.fillPolygon(new int[] {610, 630, 595, 580}, new int[] {1080, 1080, 690, 690}, 4);
      g.setColor(new Color(49, 103, 76));
      g.fillOval(445, 650, 310, 55);
      g.fillOval(540, 570, 70, 220);
    }
  }

  private static void city(Graphics2D g, Color base) {
    sun(g, 440, 95, 190);
    for (int i = 0; i < 9; i++) {
      int x = i * 100 - 80, h = 240 + i * 79 % 340, y = 840 - h;
      g.setColor(i % 2 == 0 ? base.darker() : base.darker().darker());
      g.fillRect(x, y, 90, h + 240);
      g.setColor(new Color(255, 218, 153, 170));
      for (int row = 0; row < h / 45; row++) for (int col = 0; col < 3; col++)
        g.fillRect(x + 14 + col * 24, y + 20 + row * 45, 10, 17);
    }
    g.setColor(new Color(204, 166, 139));
    g.fillPolygon(new int[] {100, 620, 395, 325}, new int[] {1080, 1080, 790, 790}, 4);
  }
}
