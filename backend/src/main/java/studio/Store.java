package studio;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface Store {
  @Select("SELECT * FROM templates ORDER BY id")
  List<Map<String, Object>> templates();

  @Select("SELECT * FROM templates WHERE id=#{id}")
  Map<String, Object> template(String id);

  @Select("SELECT id FROM visitors WHERE token=#{token}")
  String visitor(String token);

  @Insert("INSERT INTO visitors VALUES(#{id},#{token}) ON DUPLICATE KEY UPDATE token=VALUES(token)")
  void visitorAdd(@Param("id") String id, @Param("token") String token);

  @Insert("INSERT INTO assets VALUES(#{id},#{owner},#{mime},#{size},#{path})")
  void assetAdd(
      @Param("id") String id,
      @Param("owner") String owner,
      @Param("mime") String mime,
      @Param("size") long size,
      @Param("path") String path);

  @Select("SELECT * FROM assets WHERE id=#{id} AND visitor_id=#{owner}")
  Map<String, Object> asset(@Param("id") String id, @Param("owner") String owner);

  @Insert(
      "INSERT INTO tasks(id,visitor_id,template_id,title,status,current_stage,simulation)"
          + " VALUES(#{id},#{owner},#{template},#{title},'QUEUED','WAITING',#{simulation})")
  void create(
      @Param("id") String id,
      @Param("owner") String owner,
      @Param("template") String template,
      @Param("title") String title,
      @Param("simulation") String simulation);

  @Insert("INSERT INTO task_assets VALUES(#{task},#{asset},#{slot})")
  void bind(@Param("task") String task, @Param("asset") String asset, @Param("slot") int slot);

  @Insert(
      "INSERT INTO task_steps(task_id,stage,sequence_no,status)"
          + " VALUES(#{task},#{stage},#{seq},'PENDING')")
  void stepAdd(@Param("task") String task, @Param("stage") String stage, @Param("seq") int seq);

  @Select("SELECT * FROM tasks WHERE id=#{id} AND visitor_id=#{owner}")
  Map<String, Object> task(@Param("id") String id, @Param("owner") String owner);

  @Select("SELECT * FROM tasks WHERE visitor_id=#{owner} ORDER BY created_at DESC,id DESC")
  List<Map<String, Object>> history(String owner);

  @Select(
      "SELECT * FROM tasks WHERE visitor_id=#{owner} ORDER BY created_at DESC,id DESC LIMIT"
          + " #{limit} OFFSET #{offset}")
  List<Map<String, Object>> historyPage(
      @Param("owner") String owner, @Param("limit") int limit, @Param("offset") int offset);

  @Select("SELECT * FROM task_steps WHERE task_id=#{id} ORDER BY sequence_no")
  List<Map<String, Object>> steps(String id);

  @Update(
      "UPDATE tasks SET"
          + " status=#{status},current_stage=#{stage},state_version=state_version+1,error_message=#{error},failure_stage=IF(#{status}='FAILED',#{stage},NULL),result_path=#{result},finished_at=IF(#{status}"
          + " IN ('FAILED','SUCCEEDED'),CURRENT_TIMESTAMP(3),NULL) WHERE id=#{id} AND status NOT IN"
          + " ('FAILED','SUCCEEDED')")
  int update(
      @Param("id") String id,
      @Param("status") String status,
      @Param("stage") String stage,
      @Param("error") String error,
      @Param("result") String result);

  @Update(
      "UPDATE task_steps SET"
          + " status=#{status},started_at=IF(#{status}='RUNNING',CURRENT_TIMESTAMP(3),started_at),finished_at=IF(#{status}"
          + " IN ('FAILED','SUCCEEDED'),CURRENT_TIMESTAMP(3),NULL),error_message=#{error} WHERE"
          + " task_id=#{id} AND stage=#{stage}")
  void step(
      @Param("id") String id,
      @Param("stage") String stage,
      @Param("status") String status,
      @Param("error") String error);
}
