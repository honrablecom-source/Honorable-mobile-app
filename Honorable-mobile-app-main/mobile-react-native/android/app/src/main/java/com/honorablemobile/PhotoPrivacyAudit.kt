package com.honorablemobile

/** Presence only: the predicate never returns EXIF values to this workflow. */
internal object PhotoPrivacyAudit {
    const val MAX_PHOTOS = 20
    enum class Group { LOCATION, IDENTITY_TEXT, TIME }
    enum class Field(val group: Group) {
        GPS_LATITUDE(Group.LOCATION), GPS_LONGITUDE(Group.LOCATION), GPS_ALTITUDE(Group.LOCATION),
        GPS_DEST_LATITUDE(Group.LOCATION), GPS_DEST_LONGITUDE(Group.LOCATION), GPS_DATE(Group.LOCATION), GPS_TIME(Group.LOCATION),
        GPS_AREA(Group.LOCATION), GPS_PROCESSING(Group.LOCATION),
        CAMERA_OWNER(Group.IDENTITY_TEXT), BODY_SERIAL(Group.IDENTITY_TEXT),
        ARTIST(Group.IDENTITY_TEXT), COPYRIGHT(Group.IDENTITY_TEXT), DESCRIPTION(Group.IDENTITY_TEXT), USER_COMMENT(Group.IDENTITY_TEXT),
        DATE_TIME(Group.TIME), DATE_ORIGINAL(Group.TIME), DATE_DIGITIZED(Group.TIME),
    }
    fun requireCount(count: Int) { require(count in 1..MAX_PHOTOS) { "Select one to twenty photo originals" } }
    fun inspect(hasField: (Field) -> Boolean, current: () -> Unit = {}): List<Field> =
        Field.entries.filter { current(); hasField(it) }.toList().also { current() }
    fun locationFlagged(fields: List<Field>): Boolean = fields.any { it.group == Group.LOCATION }
}
