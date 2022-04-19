import com.alibaba.fastjson2.JSON;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.runtime.Settings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import com.jsoniter.JsonIterator;
import com.jsoniter.spi.DecodingMode;
import jason.JasonReader;

// --add-opens java.base/java.lang=ALL-UNNAMED
public class JsonBenchmark {
	public static class C { // 测试的类
		public int a;
		@SuppressWarnings("unused")
		public String b;
		public C c;
	}

	// 测试的JSON字符串
	static String str = "{\"b\":\"xyz\", \"a\":123,\"c\":{\"a\":456,\"b\":\"abc\"}}";
	static byte[] bytes = str.getBytes();
	static final int TEST_COUNT = 10_000_000;

	static void testJackson() throws Exception { // jackson-*-2.13.2.jar四个文件总大小2.09MB
		long t = System.nanoTime(), v = 0;
		ObjectMapper om = new ObjectMapper();
		om.registerModule(new AfterburnerModule()); // 实测这里Afterburner插件效果不理想,只提速15%
		for (int i = 0; i < TEST_COUNT; ++i) {
			C c = om.readValue(bytes, C.class);
			v += c.a + c.c.a;
		}
		System.out.println(" Jackson: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	static void testFastJson() { // fastjson2-2.0.1.jar大小1.05MB
		long t = System.nanoTime(), v = 0;
		for (int i = 0; i < TEST_COUNT; ++i) {
			// C c = JSON.parseObject(bytes, C.class);
			C c = JSON.parseObject(str, C.class); // 只有FastJson使用String输入比byte[]性能更高
			v += c.a + c.c.a;
		}
		System.out.println("FastJson: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	static void testDslJson() throws Exception { // dsl-json-1.9.9.jar和dsl-json-java8-1.9.9.jar总大小492KB
		long t = System.nanoTime(), v = 0;
		DslJson<Object> dj = new DslJson<>(Settings.withRuntime().includeServiceLoader());
		for (int i = 0; i < TEST_COUNT; ++i) {
			C c = dj.deserialize(C.class, bytes, bytes.length);
			//noinspection ConstantConditions
			v += c.a + c.c.a;
		}
		System.out.println("Dsl-Json: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	static void testJsoniter() { // jsoniter-0.9.23.jar和javassist-3.28.0-GA.jar总大小1.14MB
		long t = System.nanoTime(), v = 0;
		// 设置最快的模式,需要借助javassist库(javassist-3.26.0-GA.jar大小765KB)
		JsonIterator.setMode(DecodingMode.DYNAMIC_MODE_AND_MATCH_FIELD_WITH_HASH);
		for (int i = 0; i < TEST_COUNT; ++i) {
			C c = JsonIterator.deserialize(bytes, C.class);
			v += c.a + c.c.a;
		}
		System.out.println("Jsoniter: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	static void testJason() throws Exception { // jason.jar大小45KB
		long t = System.nanoTime(), v = 0;
		JasonReader jr = new JasonReader();
		for (int i = 0; i < TEST_COUNT; ++i) {
			C c = jr.buf(bytes).parse(C.class);
			//noinspection ConstantConditions
			v += c.a + c.c.a;
		}
		System.out.println("   Jason: " + v + ", " + (System.nanoTime() - t) / 1_000_000 + "ms");
	}

	public static void main(String[] args) throws Exception {
		// 各测试5轮,排除预热影响,以最少时间为准
		for (int i = 0; i < 5; ++i)
			testJackson();
		for (int i = 0; i < 5; ++i)
			testFastJson();
		for (int i = 0; i < 5; ++i)
			testDslJson();
		for (int i = 0; i < 5; ++i)
			testJsoniter();
		for (int i = 0; i < 5; ++i)
			testJason();
	}
}
// OpenJDK 17.0.2
//  Jackson: 5790000000, 3606ms
// FastJson: 5790000000, 1208ms
// Dsl-Json: 5790000000, 2243ms
// Jsoniter: 5790000000, 1058ms
//    Jason: 5790000000, 871ms
