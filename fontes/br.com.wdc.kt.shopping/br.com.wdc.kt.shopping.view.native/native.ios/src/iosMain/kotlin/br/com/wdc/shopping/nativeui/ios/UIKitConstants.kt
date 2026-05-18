package br.com.wdc.shopping.nativeui.ios

import platform.UIKit.*

/**
 * UIKit enum constants for Kotlin/Native 2.x interop.
 *
 * Types fall into two categories:
 * - Value classes: use Type.TypeConstantName (e.g. UIViewContentMode.UIViewContentModeScaleAspectFit)
 * - Long typealiases: use Long literal directly (e.g. 1L for UILayoutConstraintAxisVertical)
 */
object UIK {
    // UIViewContentMode (value class)
    val ContentModeScaleToFill: UIViewContentMode = UIViewContentMode.UIViewContentModeScaleToFill
    val ContentModeScaleAspectFit: UIViewContentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
    val ContentModeScaleAspectFill: UIViewContentMode = UIViewContentMode.UIViewContentModeScaleAspectFill

    // UITextBorderStyle (value class)
    val BorderStyleNone: UITextBorderStyle = UITextBorderStyle.UITextBorderStyleNone
    val BorderStyleLine: UITextBorderStyle = UITextBorderStyle.UITextBorderStyleLine
    val BorderStyleBezel: UITextBorderStyle = UITextBorderStyle.UITextBorderStyleBezel
    val BorderStyleRoundedRect: UITextBorderStyle = UITextBorderStyle.UITextBorderStyleRoundedRect

    // UITextAutocorrectionType (value class)
    val AutocorrectionDefault: UITextAutocorrectionType = UITextAutocorrectionType.UITextAutocorrectionTypeDefault
    val AutocorrectionNo: UITextAutocorrectionType = UITextAutocorrectionType.UITextAutocorrectionTypeNo
    val AutocorrectionYes: UITextAutocorrectionType = UITextAutocorrectionType.UITextAutocorrectionTypeYes

    // UITextAutocapitalizationType (value class)
    val AutocapNone: UITextAutocapitalizationType = UITextAutocapitalizationType.UITextAutocapitalizationTypeNone
    val AutocapWords: UITextAutocapitalizationType = UITextAutocapitalizationType.UITextAutocapitalizationTypeWords
    val AutocapSentences: UITextAutocapitalizationType = UITextAutocapitalizationType.UITextAutocapitalizationTypeSentences
    val AutocapAll: UITextAutocapitalizationType = UITextAutocapitalizationType.UITextAutocapitalizationTypeAllCharacters

    // UIReturnKeyType (value class)
    val ReturnKeyDefault: UIReturnKeyType = UIReturnKeyType.UIReturnKeyDefault
    val ReturnKeyGo: UIReturnKeyType = UIReturnKeyType.UIReturnKeyGo
    val ReturnKeyDone: UIReturnKeyType = UIReturnKeyType.UIReturnKeyDone

    // UIStackViewAlignment (Long typealias)
    val StackAlignFill: UIStackViewAlignment = 0L     // UIStackViewAlignmentFill
    val StackAlignLeading: UIStackViewAlignment = 1L  // UIStackViewAlignmentLeading
    val StackAlignCenter: UIStackViewAlignment = 3L   // UIStackViewAlignmentCenter
    val StackAlignTrailing: UIStackViewAlignment = 4L // UIStackViewAlignmentTrailing

    // UILayoutConstraintAxis (Long typealias)
    val AxisHorizontal: UILayoutConstraintAxis = 0L // UILayoutConstraintAxisHorizontal
    val AxisVertical: UILayoutConstraintAxis = 1L   // UILayoutConstraintAxisVertical

    // NSTextAlignment (Long typealias)
    val TextAlignLeft: NSTextAlignment = 0L   // NSTextAlignmentLeft
    val TextAlignCenter: NSTextAlignment = 1L // NSTextAlignmentCenter
    val TextAlignRight: NSTextAlignment = 2L  // NSTextAlignmentRight

    // UIActivityIndicatorViewStyle (Long typealias)
    val ActivityStyleMedium: UIActivityIndicatorViewStyle = 100L  // UIActivityIndicatorViewStyleMedium
    val ActivityStyleLarge: UIActivityIndicatorViewStyle = 101L   // UIActivityIndicatorViewStyleLarge
}
