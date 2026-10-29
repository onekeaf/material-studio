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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

class UploadTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(strings = {"png", "jpeg"})
  void damagedImagesReturn415WithoutWritingAssets(String format) throws Exception {
    Store store = mock(Store.class);
    when(store.visitor(anyString())).thenReturn("owner");
    when(store.template("portrait")).thenReturn(Map.of("id", "portrait"));
    Api api = new Api(store, mock(Pipeline.class), mock(PlatformTransactionManager.class),
        directory.toString(), true, new ObjectMapper());
    byte[] bytes = format.equals("png")
        ? new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0}
        : new byte[] {(byte) 255, (byte) 216, (byte) 255, (byte) 224, 0, 16, 74, 70, 73, 70, 0};
    MockMvcBuilders.standaloneSetup(api).build()
        .perform(multipart("/api/assets")
            .file(new MockMultipartFile("file", "damaged." + format, "image/" + format, bytes))
            .param("templateId", "portrait").cookie(new Cookie("studio", "token")))
        .andExpect(status().isUnsupportedMediaType());
    verify(store, never()).assetAdd(anyString(), anyString(), anyString(), anyLong(), anyString());
    try (var files = Files.list(directory)) {
      assertEquals(0, files.count());
    }
  }

  @Test
  void storageFailureIsNotReportedAsInvalidImage() throws Exception {
    Store store = mock(Store.class);
    when(store.visitor(anyString())).thenReturn("owner");
    when(store.template("portrait")).thenReturn(Map.of("id", "portrait"));
    Api api = new Api(store, mock(Pipeline.class), mock(PlatformTransactionManager.class),
        directory.toString(), true, new ObjectMapper());
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
    Files.delete(directory);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("studio", "token"));
    assertThrows(java.io.IOException.class, () -> api.upload("portrait",
        new MockMultipartFile("file", "valid.png", "image/png", out.toByteArray()), request));
    verify(store, never()).assetAdd(anyString(), anyString(), anyString(), anyLong(), anyString());
  }

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
