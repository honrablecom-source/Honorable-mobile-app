package app.honorable.editor

/** Portable scalar edits; no pixels, model masks, paths or credentials. */
data class PhotoEdit(val rotation:Int=0,val crop:Float=1f,val brightness:Float=0f,
    val contrast:Float=1f,val saturation:Float=1f,val exposure:Float=0f,val personCutout:Boolean=false,val curve:PhotoToneCurve=PhotoToneCurve(),val radialLight:PhotoRadialLight?=null,val background:PhotoBackground?=null,val finish:PhotoFinish=PhotoFinish(),val creative:PhotoCreative=PhotoCreative(),val effects:PhotoEffects=PhotoEffects(),val composition:PhotoComposition=PhotoComposition()) {
    val needsPersonMask:Boolean get() = personCutout || background!=null
    init {
        require(rotation in setOf(0,90,180,270))
        require(crop.isFinite() && crop in .1f..1f)
        require(brightness.isFinite() && brightness in -128f..128f)
        require(contrast.isFinite() && contrast in 0f..2f)
        require(saturation.isFinite() && saturation in 0f..2f)
        require(exposure.isFinite() && exposure in -2f..2f)
        require(!personCutout || background==null)
    }
}
