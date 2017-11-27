package nettyInActionWebSocket;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map.Entry;
import java.util.TimeZone;
import javax.activation.MimetypesFileTypeMap;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedNioFile;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.util.CharsetUtil;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

/**
 * <pre>
 * nettyInActionWebSocket |_ httpRequestHandler
 *
 * 1. 개요 : 2. 작성일 : 2017. 11. 2.
 * 
 * <pre>
 *
 * @author : 박명규(로컬계정)
 * @version : 1.0
 */
public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
	public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
	public static final int HTTP_CACHE_SECONDS = 60;

	private final String wsUri;
	public static final String DOCUMENT_ROOT = "C:/Users/박명규/git/JavaProjectWithSpring/JavaProjectSpring/WebContent";
	public static final String FILE_ROOT = "C:/Users/박명규/git/JavaProjectWithSpring/JavaProjectSpring/uploadFile";

//	static{
//		String temp = HttpRequestHandler.class.getResource("").getPath();
//		temp = temp.substring(temp.indexOf('/')+1, temp.length());
//		for (int i = 0; i < 2 ; i++)
//			temp = temp.substring(0,temp.lastIndexOf('/'));
//		FILE_ROOT= temp+"/uploadFile";
//		DOCUMENT_ROOT = temp+"/WebContent";
//		System.out.println("FILE_ROOT : "+FILE_ROOT);
//		System.out.println("DOCUMENT_ROOT : "+DOCUMENT_ROOT);
//	}
	
	public HttpRequestHandler(String wsUri) {
		
		this.wsUri = wsUri;
		
	}

	@Override
	public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
		try {

			HttpHeaders headers = request.headers();

			String uri = request.uri();
			Map<String, List<String>> parameter = new QueryStringDecoder(uri).parameters();

			System.out.println("Parameter 시작");
			for (Entry<String, List<String>> temp : parameter.entrySet()) {
				System.out.printf(temp.getKey() + " : \n");
				for (String temp2 : temp.getValue()) {
					System.out.println("       " + temp2);
				}
			}
			System.out.println("Parameter 끝");

			System.out.println("header 시작");
			for (Entry<String, String> header : headers) {
				System.out.println(header.getKey() + " : " + header.getValue());
			}
			System.out.println("header 끝");
			if (wsUri.equalsIgnoreCase(request.uri()) && "Upgrade".equalsIgnoreCase(headers.get("Connection"))
					&& "WebSocket".equalsIgnoreCase(headers.get("Upgrade"))) {
				ctx.fireChannelRead(request.retain());
				System.out.println("웹소켓 연결 시도");
			} else if (parameter.get("mode") != null && "fileDownload".equalsIgnoreCase(parameter.get("mode").get(0))) {
				fileDownload(ctx, request);
				System.out.println("파일 다운로드 시도");
			} else {
				generalHttpRequest(ctx, request);
				System.out.println("일반 문서 요청 시도");
			}
		} catch (FileNotFoundException e) {

			e.printStackTrace();
		}
	}

	public void generalHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) throws IOException {
		if (HttpUtil.is100ContinueExpected(request)) {
			send100Continue(ctx);
		}

		System.out.println("일반 HTTP 연결 시도");
		RandomAccessFile file;

		String fileName;
		if (request.uri() == null || "/".equals(request.uri())) {
			sendError(ctx, HttpResponseStatus.BAD_REQUEST);
			return;
		} else {
			if (request.uri().indexOf("?") != -1) {
				file = new RandomAccessFile(DOCUMENT_ROOT + request.uri().substring(0, request.uri().indexOf("?")), "r");
				fileName = DOCUMENT_ROOT + request.uri().substring(0, request.uri().indexOf("?"));
			} else {
				file = new RandomAccessFile(DOCUMENT_ROOT + request.uri(), "r");
				fileName = DOCUMENT_ROOT + request.uri();
			}
		}

		HttpResponse response = new DefaultHttpResponse(request.protocolVersion(), HttpResponseStatus.OK);
		// 파일의 mime 타입을 판별하기 위해 2가지 방법 사용
		String mime = Files.probeContentType(Paths.get(fileName)); 
		if (mime == null) {
			mime = MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(fileName);
		}
		System.out.println("file MimeType : " + mime);
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, mime);
		boolean keepAlive = HttpUtil.isKeepAlive(request);
		if (!keepAlive) {
			response.headers().set(HttpHeaderNames.CONTENT_LENGTH, file.length());
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

		}
		ctx.write(response); // httpResponse를 클라이언트로 전송
		if (ctx.pipeline().get(SslHandler.class) == null) { // 만약 SSL이
															// 적용되지 않았다면
			ctx.write(new DefaultFileRegion(file.getChannel(), 0, file.length()));
		} else {
			ctx.write(new ChunkedNioFile(file.getChannel()));
		}
		ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
		// 채널에 쓰기가 완료되면 파일을 닫음
		future.addListener(new GenericFutureListener<ChannelFuture>() { 
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				file.close();
			}
		});
		if (!keepAlive) { // keep-alive 요청이 없는 경우 기록이 끝나면 Channel을 닫음
			future.addListener(ChannelFutureListener.CLOSE);
		}
		ctx.channel().close();

	}

	private static void send100Continue(ChannelHandlerContext ctx) {
		FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE);
		ctx.writeAndFlush(response);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		cause.printStackTrace();
		if (ctx.channel().isActive()) {
			sendError(ctx, INTERNAL_SERVER_ERROR);
		}
	}

	public void fileDownload(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {

		if (!request.decoderResult().isSuccess()) {
			sendError(ctx, BAD_REQUEST);
			return;
		}

		if (request.method() != GET) {
			sendError(ctx, METHOD_NOT_ALLOWED);
			return;
		}

		final Map<String, List<String>> parameter = new QueryStringDecoder(request.uri()).parameters();
		
		// 파일의 경로는 FileRoot/fileUploader/fileName
		final String path = FILE_ROOT+"/"+parameter.get("fileUploader").get(0) + "/" + parameter.get("fileName").get(0); 

		File file = new File(path);
		if (file.isHidden() || !file.exists()) {
			sendError(ctx, NOT_FOUND);
			return;
		}

		if (!file.isFile()) {
			sendError(ctx, FORBIDDEN);
			return;
		}

		String ifModifiedSince = request.headers().get(HttpHeaderNames.IF_MODIFIED_SINCE);
		if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
			SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
			Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);

			long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
			long fileLastModifiedSeconds = file.lastModified() / 1000;
			if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
				sendNotModified(ctx);
				return;
			}
		}

		RandomAccessFile raf;
		try {
			raf = new RandomAccessFile(file, "r");
		} catch (FileNotFoundException ignore) {
			sendError(ctx, NOT_FOUND);
			return;
		}
		long fileLength = raf.length();

		HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
		HttpUtil.setContentLength(response, fileLength);
		setContentTypeHeader(response, file);
		setDateAndCacheHeaders(response, file);
		if (HttpUtil.isKeepAlive(request)) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		}

		ctx.write(response);

		ChannelFuture sendFileFuture;
		ChannelFuture lastContentFuture;

		System.out.println("DefaultFileRegion으로 파일 전송");
		sendFileFuture = ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength), ctx.newProgressivePromise());

		lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

		sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
			@Override
			public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
				if (total < 0) { // total unknown
					System.err.println(future.channel() + " Transfer progress: " + progress);
				} else {
					System.err.println(future.channel() + " Transfer progress: " + progress + " / " + total);
				}
			}

			@Override
			public void operationComplete(ChannelProgressiveFuture future) throws IOException {
				System.err.println(future.channel() + " Transfer complete.");
				raf.close();
			}
		});

		// Decide whether to close the connection or not.
		if (!HttpUtil.isKeepAlive(request)) {
			// Close the connection when the whole content is written out.
			lastContentFuture.addListener(ChannelFutureListener.CLOSE);
		}
	}

	private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
		FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status,
				Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

		// Close the connection as soon as the error message is sent.
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	private static void sendNotModified(ChannelHandlerContext ctx) {
		FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED);
		setDateHeader(response);

		// Close the connection as soon as the error message is sent.
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	private static void setDateHeader(FullHttpResponse response) {
		SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
		dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

		Calendar time = new GregorianCalendar();
		response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));
	}

	private static void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
		SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
		dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

		// Date header
		Calendar time = new GregorianCalendar();
		response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));

		// Add cache headers
		time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
		response.headers().set(HttpHeaderNames.EXPIRES, dateFormatter.format(time.getTime()));
		response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
		response.headers().set(HttpHeaderNames.LAST_MODIFIED,
				dateFormatter.format(new Date(fileToCache.lastModified())));
	}

	private static void setContentTypeHeader(HttpResponse response, File file) {
		// MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
		// response.headers().set(HttpHeaderNames.CONTENT_TYPE,
		// mimeTypesMap.getContentType(file.getPath()));
		response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM);
	}

}
