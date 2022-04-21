package com.lmig.libertyconnect.sms.stack.util;

import com.lmig.libertyconnect.sms.stack.LcSmsStackApp.Args;

public class StackUtils {

	public static String getPrefixedName(final String name) {
		final Args ARGS = new Args();
		return String.format("%s%s%s%s%s", ARGS.getProgram(), "-", ARGS.getProfile(), "-", name);
	}
}
