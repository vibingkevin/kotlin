#include <clang-c/Index.h>
#include <cassert>
#include <cstdio>
#include <cstring>
static unsigned Seen = 0;
static unsigned ObjCSeen = 0;
static CXChildVisitResult visit(CXCursor C, CXCursor, CXClientData) {
  if (clang_getCursorKind(C) == CXCursor_ObjCInterfaceDecl) {
    CXString Name = clang_getCursorSpelling(C);
    if ((std::strcmp(clang_getCString(Name), "ProfileObject") == 0 || std::strcmp(clang_getCString(Name), "ProfileChild") == 0)) {
      CXType T = clang_getCursorType(C);
      auto Size = clang_Type_getSizeOf(T);
      assert(Size > 0 && Size == clang_Type_getSizeOf(T));
      assert(clang_Type_getAlignOf(T) > 0);
      ++ObjCSeen;
    }
    clang_disposeString(Name);
    return CXChildVisit_Recurse;
  }
  if (clang_getCursorKind(C) != CXCursor_StructDecl) return CXChildVisit_Recurse;
  CXString Name = clang_getCursorSpelling(C);
  if (std::strcmp(clang_getCString(Name), "ProfileBasic") == 0) {
    CXType T = clang_getCursorType(C);
    auto Size = clang_Type_getSizeOf(T);
    assert(Size > 0 && Size == clang_Type_getSizeOf(T));
    assert(clang_Type_getAlignOf(T) > 0);
    assert(clang_Type_getOffsetOf(T, "value") >= 8);
    ++Seen;
  }
  clang_disposeString(Name);
  return CXChildVisit_Recurse;
}
int main(int Argc, char **Argv) {
  assert(Argc == 2);
  CXIndex Index = clang_createIndex(0, 0);
  const char *Args[] = {"-x", "objective-c", "-fsyntax-only"};
  CXTranslationUnit TU = clang_parseTranslationUnit(Index, Argv[1], Args, 3, nullptr, 0, CXTranslationUnit_None);
  assert(TU);
  for (unsigned I = 0; I < clang_getNumDiagnostics(TU); ++I) {
    auto D = clang_getDiagnostic(TU, I);
    assert(clang_getDiagnosticSeverity(D) < CXDiagnostic_Error);
    clang_disposeDiagnostic(D);
  }
  clang_visitChildren(clang_getTranslationUnitCursor(TU), visit, nullptr);
  assert(Seen == 1 && ObjCSeen == 2);
  clang_disposeTranslationUnit(TU); clang_disposeIndex(Index);
  std::puts("CLANG_LAYOUT_SMOKE_OK");
}
