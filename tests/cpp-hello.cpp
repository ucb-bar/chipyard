#include <iostream>
using namespace std;

// HACK: This is a workaround for the missing __dso_handle routine in the current toolchain
extern "C" void *__dso_handle = 0;

int main() {
  cout << "Hello World!";
  return 0;
}
