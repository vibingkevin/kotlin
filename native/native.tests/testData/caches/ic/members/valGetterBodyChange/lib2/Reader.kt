package test

fun checkedLength(holder: Holder): Int =
    if (holder.value is String) (holder.value as String).length else -1
