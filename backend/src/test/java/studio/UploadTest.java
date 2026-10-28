package studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

class UploadTest {
  @TempDir Path directory;

  @Test
  void validatesActualJpegAndPngAndPersistsBytes() throws Exception {
    Store store = mock(Store.class);
    when(store.visitor(anyString())).thenReturn("owner");
    when(store.template("portrait")).thenReturn(Map.of("id", "portrait"));
    Api api =
        new Api(
            store,
            mock(Pipeline.class),
            mock(PlatformTransactionManager.class),
            directory.toString(),
            true,
            new ObjectMapper());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("studio", "token"));
    for (String format : new String[] {"png", "jpg"}) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, out);
      byte[] bytes = out.toByteArray();
      Map<String, Object> response =
          api.upload(
              "portrait",
              new MockMultipartFile("file", "untrusted.bin", "application/octet-stream", bytes),
              request);
      assertEquals(format.equals("png") ? "image/png" : "image/jpeg", response.get("mimeType"));
      assertArrayEquals(
          bytes, Files.readAllBytes(directory.resolve(response.get("assetId").toString())));
    }
    assertThrows(
        ResponseStatusException.class,
        () ->
            api.upload(
                "portrait",
                new MockMultipartFile("file", "fake.png", "image/png", "bad".getBytes()),
                request));
    verify(store, times(2)).assetAdd(anyString(), eq("owner"), anyString(), anyLong(), anyString());
  }
}
