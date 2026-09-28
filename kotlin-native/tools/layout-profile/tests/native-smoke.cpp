#include "llvm-c/Core.h"
#include "llvm-c/Target.h"
#include <cassert>
int main() {
  auto C = LLVMContextCreate();
  auto D = LLVMCreateTargetData("e-p:64:64-i64:64");
  auto S = LLVMStructCreateNamed(C, "profile.struct");
  LLVMTypeRef F[] = {LLVMInt8TypeInContext(C), LLVMInt64TypeInContext(C)};
  LLVMStructSetBody(S, F, 2, false);
  assert(LLVMStoreSizeOfType(D, S) == 16);
  assert(LLVMStoreSizeOfType(D, S) == 16);
  assert(LLVMABIAlignmentOfType(D, S) == 8);
  assert(LLVMOffsetOfElement(D, S, 1) == 8);
  assert(LLVMPointerSize(D) == 8);
  LLVMDisposeTargetData(D); LLVMContextDispose(C);
}
