package com.collectionlogexporter;

@FunctionalInterface
interface ItemCounterLookup
{
	String apply(String pageName, int itemId);
}
