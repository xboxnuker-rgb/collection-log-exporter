package com.collectionlogexporter;

final class XmlSupport
{
	private XmlSupport()
	{
	}

	static String escape(String value)
	{
		if (value == null)
		{
			return "";
		}
		StringBuilder out = new StringBuilder(value.length() + 16);
		for (int index = 0; index < value.length(); index++)
		{
			char character = value.charAt(index);
			switch (character)
			{
				case '&': out.append("&amp;"); break;
				case '<': out.append("&lt;"); break;
				case '>': out.append("&gt;"); break;
				case '"': out.append("&quot;"); break;
				case '\'': out.append("&apos;"); break;
				default:
					if (character == '\t' || character == '\n' || character == '\r'
						|| character >= 0x20)
					{
						out.append(character);
					}
			}
		}
		return out.toString();
	}
}
