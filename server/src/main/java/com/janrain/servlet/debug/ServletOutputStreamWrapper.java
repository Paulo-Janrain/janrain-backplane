package com.janrain.servlet.debug;

import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * For use by the {@link DebugResponseLoggingFilter}
 * See http://www.java-forums.org/java-servlet/20631-how-get-content-httpservletresponse.html
 */
public class ServletOutputStreamWrapper extends ServletOutputStream {
	OutputStream _out;
	boolean closed = false;

	public ServletOutputStreamWrapper(OutputStream realStream) {
		this._out = realStream;
	}

	@Override
	public void close() throws IOException {
		if (closed) {
			throw new IOException("This output stream has already been closed");
		}
		_out.flush();
		_out.close();

		closed = true;
	}

	@Override
	public void flush() throws IOException {
		if (closed) {
			throw new IOException("Cannot flush a closed output stream");
		}
		_out.flush();
	}

	@Override
	public void write(int b) throws IOException {
		if (closed) {
			throw new IOException("Cannot write to a closed output stream");
		}
		_out.write((byte) b);
	}

	@Override
	public void write(byte b[]) throws IOException {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte b[], int off, int len) throws IOException {
		// System.out.println("writing...");
		if (closed) {
			throw new IOException("Cannot write to a closed output stream");
		}
		_out.write(b, off, len);
	}

}
